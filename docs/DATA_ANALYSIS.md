# Sentinel — Data Intelligence & Empirical Analysis (Phase 2)

## Executive Summary

Phase 2 of project Sentinel conducts a rigorous, empirical analysis of all datasets provided in the NEXORA 2026 Challenge. Every finding and specification documented below is derived from direct analysis of the actual data files (1,433,387 telemetry records across 8 months, 332 gateway master records, 6,400 meter read records, 642 field visits, and 120 engineer reviews).

No features or strategies are proposed on conceptual appeal alone; all conclusions follow the pipeline:
$$\text{DATA} \longrightarrow \text{EVIDENCE} \longrightarrow \text{FEATURES} \longrightarrow \text{RANKING DECISION}$$

---

## 1. Complete Dataset Inventory

The `/data` directory contains 6 primary sources:

| Source | Physical Format | Row Count | Unique Gateways | Date Range | MAC Format | Encoding / Details |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Telemetry Parquet** | Apache Parquet (Snappy, Hive-partitioned) | **1,433,387** | **320** | 2025-08-01 00:00:00 to 2026-03-31 23:00:00 (8 months) | 12-char hex, NO colons (`0639EA5602C1`) | 58 columns, 8 month partitions (`month=2025-08` to `month=2026-03`) |
| **Gateway Master** | CSV | **332** | **332** | Installs: 2019-06-07 to 2026-07-14; Decomms: 2025-09-22 to 2026-02-25 | 17-char colon hex (`06:39:EA:56:02:C1`) | **ISO-8859-1 (Latin-1)** due to German umlauts/eszett (`Außenmast`, `Gebäude`) |
| **Meter Read Success** | CSV | **6,400** | **256** | 2025-08-04 to 2026-01-26 (26 weekly snapshots) | 12-char hex, NO colons (`02097AB58D3C`) | UTF-8, columns: `gateway_id`, `week_start`, `meters_read`, `meters_expected` |
| **Field Visits** | CSV | **642** | **247** | Visited: 2025-02-05 to 2026-02-14 (Requested: 2025-02-03 to 2026-01-30) | 17-char colon hex (`02:30:EE:F7:24:35`) | UTF-8, columns: `visit_id`, `gateway_id`, `requested_on`, `visited_on`, `reason_reported`, `outcome`, `parts_replaced`, `technician_hours` |
| **Engineer Review** | Excel (`.xlsx`) | **120** | **120** | `2026-02-14` (Excel date serial `46068`) | 17-char colon hex (`06:5B:92:87:16:CD`) | 1 sheet (`Gateway Status`), reviewer `M. Hoffmann`, 60 `Schlecht` / 60 `Normal` |
| **Telemetry Sample** | CSV | **181,484** | **280** | 2025-08-01 00:00:00 to 2025-08-31 23:00:00 | 12-char hex, NO colons | Unpartitioned CSV copy of August 2025 partition |

---

## 2. Telemetry Deep Analysis

### 2.1 Volume & Coverage Distribution
- **Total observations**: 1,433,387 gateway-hours.
- **Maximum theoretical hours** across 8 months (243 days): 5,832 hours.
- **Observed hours per gateway**:
  - Min: 261 hours (new installation `0E5F36E62604` deployed 2026-03-15)
  - 10th percentile: 1,563 hours
  - 25th percentile: 4,186 hours
  - Median: 5,166 hours
  - 75th percentile: 5,500 hours
  - 90th percentile: 5,641 hours
  - Max: 5,752 hours
  - Mean: 4,479 hours
- **Duplicate gateway-hour observations**:
  - Exactly **6,547 gateway-hours** have duplicate rows (count = 2). Total duplicate records: 13,094 rows (0.91% of dataset).
  - *Mitigation requirement*: De-duplication or aggregation (`GROUP BY gateway_id, ts_utc` or `ARG_MAX`) is required during data loading to prevent double-counting anomalies.

### 2.2 Active Fleet Growth Over Time
The active gateway count increases across months as new units are commissioned:
- `2025-08`: 280 gateways (181,484 rows)
- `2025-09`: 280 gateways (177,308 rows)
- `2025-10`: 279 gateways (178,698 rows)
- `2025-11`: 276 gateways (172,421 rows)
- `2025-12`: 274 gateways (175,850 rows)
- `2026-01`: 288 gateways (181,470 rows)
- `2026-02`: 302 gateways (170,151 rows)
- `2026-03`: 308 gateways (196,005 rows)

All 320 distinct telemetry gateways map 100% to records in `gateway_master.csv`.

### 2.3 Metric Variation & Sparsity Audit

Analyzing all 58 columns reveals that many metrics are either completely unpopulated or zero-dominated:

#### A. Dead / Zero-Variance Columns (DO NOT USE)
The following columns contain zero variance or represent foreign cellular operators not active in Germany:
- `operator_3AT`: 0 active hours (0.0%)
- `operator_A1`: 0 active hours (0.0%)
- `operator_Eplus`: 0 active hours (0.0%)
- `operator_OrangeLU`: 0 active hours (0.0%)
- `operator_Salt`: 0 active hours (0.0%)
- `operator_Swisscom`: 0 active hours (0.0%)
- `operator_TmobileA`: 0 active hours (0.0%)

*Decision*: Exclude all foreign operator columns from ranking and feature extraction.

#### B. Active Cellular Operators & Network Modes
- **Active Operators**:
  - `operator_TelekomDE`: 5,669,744 channel-hours (53.7%)
  - `operator_O2DE`: 3,371,398 channel-hours (31.9%)
  - `operator_VodafoneDE`: 897,253 channel-hours (8.5%)
  - `operator_unknown`: 617,165 channel-hours (5.8%)
- **Active Network Generations**:
  - `network_4g`: 7,748,009 channel-hours (79.9%)
  - `network_3g`: 1,647,799 channel-hours (17.0%)
  - `network_2g`: 297,395 channel-hours (3.1%)

#### C. Extreme Zero-Inflation in Error Metrics
Several error signals are zero for the vast majority of healthy hours:
- `reboot_cnt`: **98.4% of hours are 0**. (Non-zero in only 1.6% of hours).
- `offline_duration_sec`: **77.3% of hours are 0**. (Non-zero in 22.7% of hours).
- `disconnection_cnt`: **77.3% of hours are 0**. (Identical zero pattern to offline duration).
- `load1_bigger1`: **79.3% of hours are 0**.
- `load1_bigger2`: **99.3% of hours are 0**.
- `rssi_bad`: **92.8% of hours are 0**.
- `rscp_rsrp_bad`: **99.3% of hours are 0**.
- `tx_success`: **89.5% of hours are 0**.

*Crucial Statistical Implication*: Because metrics like `reboot_cnt` and `offline_duration_sec` have $\ge 77\%$ zeros, standard deviations across 28 days can be very small for quiescent gateways. An isolated single reboot or brief 10-second blip can easily exceed 3 standard deviations ($3\sigma$), causing false-positive anomaly spikes in uncalibrated baseline algorithms.

---

## 3. Gateway Master Empirical Profile

`gateway_master.csv` contains 332 records with 100% unique identifiers:
- **Encoding**: ISO-8859-1 (Latin-1). UTF-8 parsers crash on umlauts and German Eszett (`Außenmast`, `Gebäude`).
- **Decommissioned gateways**: Exactly **12 gateways** have `decommissioned_on` populated (dates between 2025-09-22 and 2026-02-25).
  - Cross-referencing with telemetry confirms that all 12 decommissioned gateways stopped producing telemetry immediately around their decommission dates (e.g., `06:90:3F:98:8F:32` decommissioned 2025-09-22, last telemetry 2025-09-21 23:00 UTC).
  - Gateways decommissioned prior to a scored week MUST be excluded from the visit candidate pool.
- **Future install dates**: Exactly 12 gateways have `installed_on` dates in May–July 2026 (future relative to the dataset), with 0 telemetry records.
- **Hardware Distribution**:
  - `GW-2100`: 182 gateways (54.8%)
  - `GW-2100L`: 101 gateways (30.4%)
  - `GW-3400`: 48 gateways (14.5%)
  - `GW-8800X`: 1 gateway (0.3% — gateway `0A5D017E401E`, which experienced 5 repeat field visits!)
- **Site Types**:
  - `Gebäude` (Building): 151 (45.5%)
  - `Heizraum` (Boiler room): 61 (18.4%)
  - `Außenmast` (Outdoor pole): 53 (16.0%)
  - `Schaltschrank` (Control cabinet): 45 (13.6%)
  - `Kellerraum` (Basement): 22 (6.6%)
- **Meters Installed**:
  - Min: 40 meters
  - 25th percentile: 110 meters
  - Median: 156 meters
  - Mean: 189 meters
  - 75th percentile: 241 meters
  - Max: 822 meters (gateway `0259991BA036`)

---

## 4. Field Visits Empirical Intelligence

Analysis of `field_visits.csv` (642 visits across 247 gateways) yields vital operational truths regarding technician dispatch economics:

### 4.1 Outcomes Breakdown
- `Kein Fehler gefunden` (No error found / Wasted truck roll): **390 visits (60.7%)**
- `Fehler behoben` (Fault resolved / Productive visit): **223 visits (34.7%)**
- `Kein Zugang` (No access): **29 visits (4.5%)**

60.7% of historical visits resulted in no defect being found (`Kein Fehler gefunden`).

### 4.2 Reported Reason vs. Actual Productive Outcome

| Reason Reported | Total Visits | Fault Resolved (`Fehler behoben`) | No Fault Found (`Kein Fehler gefunden`) | No Access (`Kein Zugang`) | Resolution Rate (True Positive %) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Keine Verbindung** (No connection) | 100 | **65** | 32 | 3 | **65.0%** |
| **Haeufige Neustarts** (Frequent reboots) | 110 | **62** | 47 | 1 | **56.4%** |
| **Kunde meldet Ausfall** (Customer reported) | 101 | **54** | 46 | 1 | **53.5%** |
| **Zaehler nicht gelesen** (Meters unread) | 86 | **42** | 42 | 2 | **48.8%** |
| **Auffaellige Statistik** (Statistical anomaly) | 87 | **0** | **77** | 10 | **0.0%** (88.5% no defect found) |
| **Signal schwach** (Weak radio signal) | 79 | **0** | **73** | 6 | **0.0%** (92.4% no defect found) |
| **Routinepruefung** (Routine inspection) | 79 | **0** | **73** | 6 | **0.0%** (92.4% no defect found) |

### 4.3 Concrete Evidence for Feature Selection
1. **Radio Signal ("Signal schwach")**: Dispatches based on weak RSSI/RSRP had a **0% resolution rate** (0 out of 79 visits fixed a fault). Weak signal is a permanent physical condition of basement/boiler room sites, not a fixable malfunction. Using raw radio metrics to trigger visits directly leads to wasted truck rolls (€380 penalty).
2. **Abstract Anomaly Counts ("Auffaellige Statistik")**: Dispatches based on statistical flags alone had a **0% resolution rate** (0 out of 87 resolved faults).
3. **Hard Failure Signals**: The only reasons that produced productive hardware/firmware repairs were:
   - Complete connection loss (`Keine Verbindung` -> 65% productive)
   - Repeated crash/reboot cycles (`Haeufige Neustarts` -> 56.4% productive)
   - Meter read failures (`Zaehler nicht gelesen` -> 48.8% productive)
4. **Replaced Parts**: When faults were fixed (223 visits), technicians replaced:
   - `Netzteil` (Power supply): 39
   - `Antenne` (Antenna replacement): 37
   - `Kabel` (Cables): 35
   - `Gateway getauscht` (Full gateway swap): 30
   - `SIM-Karte` (SIM card): 25

---

## 5. Meter Read Success Deep Analysis

`meter_read_success.csv` provides weekly aggregates across 26 calendar weeks (2025-08-04 through 2026-01-26) for 256 gateways:
- **Columns**: `gateway_id`, `week_start`, `meters_read`, `meters_expected`.
- **Success rate**: Calculated as $100 \times \frac{\text{meters\_read}}{\text{meters\_expected}}$.
- **Distribution**:
  - Healthy baseline: 80% of gateway-weeks maintain $>85\%$ read success.
  - Degraded cohort: A subset of gateways suffer chronic failure ($<50\%$ success), e.g., `02C0F45F31E7` (26.1% average across 26 weeks), `06903F988F32` (30.5%), `0AC437023B18` (35.0%).
- **Correlation with Telemetry**:
  - Gateways with low meter read success correlate moderately with high offline duration ($r = -0.23$) and disconnections ($r = -0.21$).
- **Lag and Availability Limitation**:
  - The latest record in `meter_read_success.csv` is dated `2026-01-26`.
  - For scoring weeks in March 2026, meter read records have a **5-week lag**.
  - *Decision*: Meter read success is a vital **corroborating historical feature** and validation signal, but CANNOT be the primary real-time ranking signal for March 2026 without risking stale attribution.

---

## 6. Engineer Review Deep Analysis

`engineer_review_2026-02.xlsx` contains manual qualitative evaluations for 120 gateways conducted on 2026-02-14:
- Evaluator: M. Hoffmann (single senior engineer).
- Categorization: Exactly balanced: 60 `Normal` (Healthy), 60 `Schlecht` (Poor).
- Top annotations:
  - `seit Monaten auffaellig` (13 gateways) — Persistent historical failure.
  - `haeufige Ausfaelle, Standort pruefen` (12 gateways) — Site/installation issue.
  - `Kunde hat mehrfach reklamiert` (10 gateways) — Customer impact.
  - `Hardware vermutlich defekt` (9 gateways) — True hardware failure candidate.
  - `wiederholt neu gestartet` (9 gateways) — Power/firmware bootloop.
  - `nach Tausch stabil` (6 gateways) — Successfully resolved post-swap.
- *Decision*: The Engineer Review provides ground-truth qualitative validation for backtesting our Phase 3 scoring model against expert human judgment, but CANNOT be used as an input feature for online scoring (static one-time snapshot from Feb 2026).
