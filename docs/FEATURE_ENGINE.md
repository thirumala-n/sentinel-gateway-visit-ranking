# Sentinel Feature Extraction Engine — Phase 3 Specification

## 1. Executive Summary & Design Principles

The Feature Extraction Engine converts raw physical telemetry, master inventory records, and meter reading logs into deterministic, leak-free feature vectors for gateway prioritization.

The design strictly obeys the following core operational principles discovered in Phase 2:
1. **Mathematical Anti-Leakage**: All feature calculations operate strictly on data prior to $T_{\text{target}}$ (00:00:00 UTC Monday).
2. **Deduplication by Definition**: Duplicate parquet records for the same $(gateway\_id, timestamp)$ tuple are resolved using $MAX(\cdot)$ aggregation before feature math.
3. **Physical Bounding**: Telemetry metrics are clamped to physical bounds ($0 \le offline\_duration\_sec \le 3600$, $0 \le reboot\_cnt \le 3600$).
4. **Zero-Variance Stability**: Standard deviation floors ($\sigma_{\min}$) guarantee no division by zero or infinite z-scores.
5. **Discrete Hourly Adjacency**: Consecutive offline runs check true 1-hour timestamp progression, preventing skipped-hour false bridges.

---

## 2. Temporal Boundaries & Baseline Definitions

For any target evaluation Monday $T_{\text{target}}$:

| Window Name | Start Bound (UTC, Inclusive) | End Bound (UTC, Exclusive) | Duration | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Baseline Window** | $T_{\text{target}} - 28\text{ days}$ | $T_{\text{target}}$ | 672 hours (4 weeks) | Computing $\mu$ and $\sigma$ for z-score baselines |
| **Scoring Window** | $T_{\text{target}} - 7\text{ days}$ | $T_{\text{target}}$ | 168 hours (1 week) | Evaluating recent anomalies and trajectory |
| **Trajectory Window 1 (Recent)** | $T_{\text{target}} - 72\text{ hours}$ | $T_{\text{target}}$ | 72 hours (3 days) | Recent numerator for F04 trend ratio |
| **Trajectory Window 2 (Prior)** | $T_{\text{target}} - 168\text{ hours}$ | $T_{\text{target}} - 72\text{ hours}$ | 96 hours (4 days) | Prior denominator for F04 trend ratio |

### Strict Non-Leakage Contract
Any record with timestamp $t \ge T_{\text{target}}$ is completely excluded from both the baseline and scoring datasets. The right boundary is strictly half-open $[T_{\text{start}}, T_{\text{end}})$.

---

## 3. Parquet Deduplication & Physical Clamping

### 3.1 Deduplication Contract (DuckDB Query)
```sql
SELECT
    TRIM(UPPER(REPLACE(gateway_id, ':', ''))) AS gateway_id,
    to_timestamp(ts_utc) AS ts,
    LEAST(GREATEST(MAX(offline_duration_sec), 0.0), 3600.0) AS offline_duration_sec,
    GREATEST(MAX(reboot_cnt), 0) AS reboot_cnt
FROM read_parquet(?)
WHERE ts_utc >= ? AND ts_utc < ?
GROUP BY 1, 2
ORDER BY 2 ASC
```
- **Aggregation**: `MAX(...)` handles concurrent or redundant log transmissions.
- **Bounds**: `offline_duration_sec` is clamped to $[0.0, 3600.0]$. `reboot_cnt` is clamped to non-negative integers.
- **Projection**: Only queried metrics are read from disk; `SELECT *` is prohibited to optimize memory footprint and I/O.

---

## 4. Feature Computation Specifications (F01 – F08)

### F01: Flagged Offline Hours
- **Definition**: Number of hours in the 7-day scoring window ($N \le 168$) where offline duration exceeds the 3-sigma dynamic baseline threshold.
- **Formula**:
  $$\text{Threshold}_{\text{offline}} = \mu_{\text{offline}} + 3.0 \times \max(\sigma_{\text{offline}}, 60.0)$$
  $$\text{F01} = \sum_{h=1}^{K} \mathbb{I}\left(\text{offline\_duration\_sec}_h > \text{Threshold}_{\text{offline}}\right)$$
- **Sigma Floor**: $\sigma_{\min} = 60.0\text{ seconds}$. Prevents spurious flagging of gateways with zero or microscopic baseline variance.

### F02: Flagged Reboot Hours
- **Definition**: Number of hours in the 7-day scoring window where reboot count exceeds the 3-sigma baseline threshold.
- **Formula**:
  $$\text{Threshold}_{\text{reboot}} = \mu_{\text{reboot}} + 3.0 \times \max(\sigma_{\text{reboot}}, 0.5)$$
  $$\text{F02} = \sum_{h=1}^{K} \mathbb{I}\left(\text{reboot\_cnt}_h > \text{Threshold}_{\text{reboot}}\right)$$
- **Sigma Floor**: $\sigma_{\min} = 0.5$. Prevents stable 0-reboot gateways from flagging on a single routine firmware maintenance reboot.

### F03: Max Consecutive Offline Hours (Persistence)
- **Definition**: The maximum continuous sequence of hours where offline duration meets or exceeds the critical persistence threshold ($3000\text{ seconds} \approx 50\text{ minutes}$).
- **Adjacency Invariant**: Two hours $h_i$ and $h_j$ are only consecutive if:
  $$t_j - t_i = 3600\text{ seconds}$$
  A missing hour or gap (e.g., 10:00 to 12:00 without 11:00) strictly breaks the run.
- **Threshold**: $\ge 3000.0\text{ s}$.
- **Range**: $[0, 168]$.

### F04: Trend Trajectory Ratio (Acceleration)
- **Definition**: Ratio of total offline duration in the most recent 72 hours compared to the normalized preceding 96 hours of the scoring week.
- **Formula**:
  $$\text{F04} = \min\left(5.0, \frac{\sum_{t \in [T-72h, T)} \text{offline\_sec}_t}{\left(\sum_{t \in [T-168h, T-72h)} \text{offline\_sec}_t\right) \times \frac{72}{96} + 3600.0}\right)$$
- **Laplace Smoothing**: $+3600.0\text{ s}$ in the denominator guarantees numeric stability when preceding offline time is zero.
- **Clamping**: Clamped to upper ceiling of $5.0$. Gateways with insufficient data ($< 24\text{ h}$) return neutral trajectory $1.0$.

### F05: Connected Meters Exposure
- **Definition**: Installed meter count reflecting operational and customer blast radius.
- **Formula**:
  $$\text{F05} = \max(0, \text{n\_meters\_installed})$$
  Missing or invalid records default to fleet median $156$.

### F06: Cumulative Excess Z-Score
- **Definition**: Integrated intensity of anomaly excess over the 3-sigma boundary across the scoring week.
- **Formula**:
  $$z_h = \frac{\text{offline\_duration\_sec}_h - \mu_{\text{offline}}}{\max(\sigma_{\text{offline}}, 60.0)}$$
  $$\text{Hourly Excess}_h = \min\left(10.0, \max(0.0, z_h - 3.0)\right)$$
  $$\text{F06} = \sum_{h=1}^{K} \text{Hourly Excess}_h$$
- **Capping**: Each hour is capped at $10.0$ excess to prevent single extreme events from completely dominating long-term persistent degradation.

### F07: Historic Meter Read Success Rate
- **Definition**: Aggregated 26-week meter read success fraction extracted from `meter_read_success.csv`.
- **Formula**:
  $$\text{F07} = \frac{\sum \text{meters\_read}}{\sum \text{meters\_expected}}$$
  Missing gateways or unrecorded weeks fall back to fleet benchmark median ($0.910 \approx 91.0\%$).

### F08: Disconnection Frequency
- **Definition**: Total count of status transitions from normal/active ($< 300\text{ s}$ offline) to degraded/offline ($\ge 300\text{ s}$ offline) during the scoring week. Identifies unstable, oscillating connections.

---

## 5. Gateway Lifecycle Eligibility Rules

Filtering is applied at the query level in `DuckDbGatewayRepository` and validated in domain tests:

```sql
WHERE (TRY_CAST(decommissioned_on AS DATE) IS NULL
       OR TRY_CAST(decommissioned_on AS DATE) >= CAST(? AS DATE))
  AND (TRY_CAST(installed_on AS DATE) IS NULL
       OR TRY_CAST(installed_on AS DATE) <= CAST(? AS DATE))
```

1. **Decommissioned Gateways (DQ-04)**: Gateways with `decommissioned_on < T_target` are excluded. No technicians are dispatched to retired assets.
2. **Future Installations (DQ-05)**: Gateways with `installed_on > T_target` are excluded. Assets not yet physically installed cannot experience failures.
3. **Encoding Resiliency (DQ-02)**: Latin-1 encoding preserves German location and operator metadata without corrupting text fields.

---

## 6. Edge Cases & Missing Data Handling

| Scenario | Behavior | Domain Feature Impact |
| :--- | :--- | :--- |
| **Missing Scoring Telemetry** | Gateway completely absent from parquet logs during scoring week | Classifies as `DataConfidence.MISSING_TELEMETRY`. Features set to maximum urgency (`f01 = 168`, `f03 = 168`, `f04 = 5.0`). |
| **Cold Start Baseline** | Gateway has $< 336$ hours in 28-day baseline window | Classifies as `DataConfidence.LOW_HISTORY`. Feature engine computes using available samples with enforced $\sigma_{\min}$. |
| **Zero Variance** | Gateway has constant 0 offline duration for 28 days | $\sigma = 0 \to \sigma_{\text{eff}} = 60.0$. Anomaly threshold becomes $0 + 3 \times 60 = 180\text{ s}$. No false alarms. |
| **Missing Meter Read Record** | Gateway not listed in `meter_read_success.csv` | Defaults to fleet median $91.0\%$ ($0.910$). |
| **Gapped Telemetry** | Offline hours at 10:00 and 12:00 without 11:00 record | $t_{12} - t_{10} = 7200\text{ s} \ne 3600\text{ s}$. Consecutive count resets; max run remains 1. |

---

## 7. Verification Summary

All feature computers, domain models, eligibility filters, and anti-leakage invariants are validated by 109 automated unit and architectural tests:
- `BaselineStatsTest` (16 tests)
- `GatewayEligibilityTest` (14 tests)
- `GatewayIdTest` (11 tests)
- `PredictionWeekTest` (17 tests)
- `F01F02FlaggedHoursTest` (9 tests)
- `F03ConsecutiveOfflineTest` (12 tests)
- `F04TrendTrajectoryTest` (9 tests)
- `F06CumulativeExcessZTest` (7 tests)
- `TemporalLeakageTest` (8 tests)
- `ArchitectureTest` (5 Clean Architecture rules)
- `SentinelApplicationTests` (1 Spring context boot test)
