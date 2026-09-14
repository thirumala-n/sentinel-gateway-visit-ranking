# Sentinel — Data Quality & Anomaly Audit (Phase 2)

## Overview

This document formalizes the complete data-quality audit conducted across the NEXORA challenge datasets. All discovered data anomalies, inconsistencies, and edge cases are cataloged with severity levels, operational impact, and mitigation protocols for Phase 3 ingestion.

---

## 1. Data Quality Issue Matrix

| Issue ID | Category | Description & Measured Occurrence | Severity | System Action & Resolution Protocol |
| :--- | :--- | :--- | :--- | :--- |
| **DQ-01** | Identifier Inconsistency | **MAC address format mismatch**: `gateway_master.csv`, `field_visits.csv`, and `engineer_review_2026-02.xlsx` use 17-character colon-delimited MACs (`06:39:EA:56:02:C1`). `telemetry/*.parquet` and `meter_read_success.csv` use 12-character uppercase hexadecimal strings without colons (`0639EA5602C1`). | **CRITICAL** | **Mandatory Normalization**: Ingestion layer must sanitize all gateway identifiers to normalized 12-character uppercase alphanumeric string (`REPLACE(UPPER(gateway_id), ':', '')`). Without this, all cross-table joins fail with 0 matches. |
| **DQ-02** | File Encoding | **Non-UTF-8 Encoding in Master Data**: `gateway_master.csv` contains German umlauts and eszett (`Außenmast`, `Gebäude`, `Baden-Württemberg`) encoded in **ISO-8859-1 (Latin-1)**. Standard UTF-8 readers fail with invalid byte sequence errors. | **CRITICAL** | **Encoding Specification**: Explicitly configure DuckDB and CSV readers with `encoding='latin-1'` (or `iso-8859-1`) when querying `gateway_master.csv`. |
| **DQ-03** | Telemetry Deduplication | **Duplicate Gateway-Hour Records**: Exactly **6,547 gateway-hour pairs** have duplicate records (2 rows per hour), totaling 13,094 rows (0.91% of telemetry). | **HIGH** | **Deterministic Deduplication**: Ingestion queries must execute `GROUP BY gateway_id, ts_utc` or select latest record via `ARG_MAX` over ingestion timestamp, ensuring no hour is double-weighted in anomaly calculations. |
| **DQ-04** | Lifecycle Invalidation | **Decommissioned Gateways**: 12 gateways in `gateway_master.csv` have `decommissioned_on` populated between 2025-09-22 and 2026-02-25. Telemetry confirms they ceased transmitting upon decommission. | **HIGH** | **Candidate Pool Exclusion**: If a gateway was decommissioned prior to the scoring evaluation date, it MUST be excluded from the visit ranking candidate pool. Visiting a decommissioned gateway wastes €380. |
| **DQ-05** | Future Installs / Phantom Rows | **Future Installation Dates**: 12 gateways in `gateway_master.csv` have `installed_on` dates between 2026-05-07 and 2026-07-14 and have zero telemetry records. | **MEDIUM** | **Installation Cutoff Filter**: Filter out any gateway where `installed_on > scoring_cutoff_date`. |
| **DQ-06** | History Truncation / Cold Starts | **Variable Observation History**: Gateways deployed mid-competition (e.g., `0E5F36E62604` deployed 2026-03-15) have only 261 hours of telemetry, compared to 5,500+ hours for mature units. | **HIGH** | **Confidence Degradation**: Gateways with $< 336$ hours (14 days) of history cannot produce a statistically robust 28-day baseline. Compute a `data_confidence_score` $\in [0, 1]$ and flag cold-start units for manual oversight. |
| **DQ-07** | Temporal Lag in Auxiliary Data | **Stale Meter Read Success**: `meter_read_success.csv` ends on `2026-01-26`. When scoring week `2026-03-03` to `2026-03-09`, meter read data is 5 weeks stale. | **MEDIUM** | **Secondary Signal Role**: Use meter read success strictly as an aggregated long-term reliability baseline or explanation feature, not as a rapid real-time detector for fresh weekly failures. |
| **DQ-08** | Zero-Variance Collapse | **Zero-Dominated Telemetry Metrics**: Metrics like `reboot_cnt` (98.4% zero) and `offline_duration_sec` (77.3% zero) have $\sigma \approx 0$ for stable gateways. Direct division by $\sigma$ creates division-by-zero or extreme inflated z-scores ($z > 100$) on tiny blips. | **CRITICAL** | **Variance Floor Regularization**: Implement a minimum standard deviation floor ($\sigma_{\min}$) or Laplace-smoothed baseline: $\sigma_{\text{eff}} = \max(\sigma, \sigma_{\min})$. |
| **DQ-09** | Extreme Outliers / Unbounded Durations | **Extreme Telemetry Values**: Certain hours record `offline_duration_sec` up to values exceeding normal limits due to timestamp clock skews or reconnection latencies. | **MEDIUM** | **Clamping / Capping**: All hourly durations capped to physical maximum (3,600 seconds per hour). All load values clamped to $[0, 50]$. |
| **DQ-10** | Selection Bias in Historical Visits | **High Frequency of No Defect Found in Field Visits**: 60.7% of historical visits resulted in no defect being found (`Kein Fehler gefunden`), with 0% defect resolutions for visits dispatched for weak signal or routine check. | **HIGH** | **Leakage & Bias Prevention**: Historical visit outcomes must NOT be used as positive ground-truth labels for training supervised models. Only use for retrospective validation and cost simulations. |

---

## 2. Severity Classification Framework

- **CRITICAL**: Would cause runtime exceptions (e.g. file reading crash, zero joins, division by zero) or complete failure of the ranking pipeline. Must be mitigated in the core ingestion layer.
- **HIGH**: Would cause incorrect rankings, wasted technician visits (€380 each), or distorted statistical thresholds. Requires programmatic guards and filtering.
- **MEDIUM**: Affects edge-case gateways (cold-starts, future installs, minor latency). Requires confidence adjustments and clamping.
- **LOW**: Informational or aesthetic discrepancies that do not affect mathematical ranking output.

---

## 3. Data Cleansing & Ingestion Specification for Phase 3

During DuckDB data loading in Phase 3:
1. **Sanitize Gateway ID**: `TRIM(UPPER(REPLACE(gateway_id, ':', '')))` across all datasets.
2. **Handle ISO-8859-1 Encoding**: In DuckDB, read `gateway_master.csv` with `encoding='latin-1'`.
3. **Filter Active Fleet**:
   ```sql
   WHERE (decommissioned_on IS NULL OR decommissioned_on >= :eval_start_date)
     AND (installed_on IS NULL OR installed_on <= :eval_end_date)
   ```
4. **Deduplicate Telemetry**:
   ```sql
   SELECT gateway_id, ts_utc,
          MAX(disconnection_cnt) AS disconnection_cnt,
          MAX(offline_duration_sec) AS offline_duration_sec,
          MAX(reboot_cnt) AS reboot_cnt,
          AVG(avg_load1) AS avg_load1,
          AVG(avg_memfree) AS avg_memfree,
          ...
   FROM read_parquet('...')
   GROUP BY gateway_id, ts_utc
   ```
5. **Enforce Physical Bounds**:
   - $\text{offline\_duration\_sec} = \min(3600, \max(0, \text{offline\_duration\_sec}))$
   - $\text{disconnection\_cnt} = \max(0, \text{disconnection\_cnt})$
   - $\text{reboot\_cnt} = \max(0, \text{reboot\_cnt})$
