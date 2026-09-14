# Sentinel — Machine-Readable Feature Specification (Phase 2 Contract)

## Overview

This specification establishes the authoritative, binding contract for Phase 3 feature engineering and ranking. Every feature definition, data source, mathematical formula, leakage boundary, and operational decision is codified below.

---

## 1. Feature Specification Registry

```yaml
features:
  - id: F01_ANOM_HOURS_OFFLINE
    name: Flagged Offline Hours
    definition: Count of hours in the scoring window where offline_duration_sec exceeds baseline threshold
    source: telemetry/*.parquet (offline_duration_sec)
    window: Trailing 7 days [T_eval - 7d, T_eval)
    formula: "SUM(CASE WHEN offline_duration_sec > (mu_28d + 3.0 * sigma_eff) THEN 1 ELSE 0 END)"
    availability: 100% of telemetry-reporting gateways
    leakage_risk: None (strictly past data within scoring window)
    data_quality_reqs:
      - Deduplicated on (gateway_id, ts_utc)
      - Minimum sigma floor: sigma_eff = MAX(sigma_28d, 60.0) seconds
    explainability: "Device experienced N hours of abnormal offline time this week"
    decision: PRIMARY

  - id: F02_ANOM_HOURS_REBOOT
    name: Flagged Reboot Hours
    definition: Count of hours in the scoring window where reboot_cnt exceeds baseline threshold
    source: telemetry/*.parquet (reboot_cnt)
    window: Trailing 7 days [T_eval - 7d, T_eval)
    formula: "SUM(CASE WHEN reboot_cnt > (mu_28d + 3.0 * sigma_eff) THEN 1 ELSE 0 END)"
    availability: 100% of telemetry-reporting gateways
    leakage_risk: None
    data_quality_reqs:
      - Minimum sigma floor: sigma_eff = MAX(sigma_28d, 0.5) reboots
    explainability: "Device rebooted abnormally often during N hours this week"
    decision: PRIMARY

  - id: F03_MAX_CONSECUTIVE_OFFLINE
    name: Maximum Consecutive Offline Duration
    definition: Longest continuous unbroken sequence of hours with near-total outage
    source: telemetry/*.parquet (offline_duration_sec)
    window: Trailing 7 days [T_eval - 7d, T_eval)
    formula: "MAX_RUN(offline_duration_sec >= 3000)"
    availability: 100% of telemetry-reporting gateways
    leakage_risk: None
    data_quality_reqs:
      - Continuous timeline interpolation (missing hours treated as offline)
    explainability: "Device suffered uninterrupted outage of N consecutive hours"
    decision: PRIMARY

  - id: F04_TREND_TRAJECTORY_RATIO
    name: Outage Trajectory Ratio
    definition: Ratio of recent 72-hour offline duration to preceding 96-hour offline duration
    source: telemetry/*.parquet (offline_duration_sec)
    window: Trailing 7 days split into [T_eval - 72h, T_eval) vs [T_eval - 168h, T_eval - 72h)
    formula: "SUM(offline_duration_sec[last_72h]) / (SUM(offline_duration_sec[prev_96h]) * (72.0/96.0) + 3600.0)"
    availability: Requires >= 168h active history
    leakage_risk: None
    data_quality_reqs:
      - Laplace smoothing denominator (+ 3600.0 sec) to avoid zero-division
      - Clamped between [0.0, 5.0]
    explainability: "Failure rate is accelerating (>1.0) or recovering (<1.0)"
    decision: PRIMARY

  - id: F05_METERS_EXPOSURE
    name: Installed Meter Count
    definition: Total count of smart meters connected to and dependent upon the gateway
    source: gateway_master.csv (n_meters_installed)
    window: Static master metadata as of T_eval
    formula: "COALESCE(n_meters_installed, 156)"
    availability: 100% in gateway_master.csv (median fallback = 156)
    leakage_risk: None
    data_quality_reqs:
      - Normalized MAC join: REPLACE(UPPER(gateway_id), ':', '')
      - Clamped between [1, 1000]
    explainability: "Failure directly impacts N customer meters"
    decision: PRIMARY

  - id: F06_CUMULATIVE_EXCESS_Z
    name: Cumulative Excess Z-Score
    definition: Integrated sum of standardized excess deviation above 3-sigma
    source: telemetry/*.parquet (offline_duration_sec, reboot_cnt)
    window: Trailing 7 days [T_eval - 7d, T_eval)
    formula: "SUM(MAX(0.0, ((X_t - mu_28d) / sigma_eff) - 3.0))"
    availability: 100% of telemetry-reporting gateways
    leakage_risk: None
    data_quality_reqs:
      - Hourly excess capped at 10.0 to prevent outlier distortion
    explainability: "Vertical severity index of statistical deviation"
    decision: SECONDARY

  - id: F07_HISTORIC_READ_SUCCESS
    name: Historic Meter Read Success Rate
    definition: 26-week average percentage of scheduled meter reads successfully collected
    source: meter_read_success.csv
    window: Historic snapshot up to 2026-01-26
    formula: "100.0 * SUM(meters_read) / SUM(meters_expected)"
    availability: 256 of 320 gateways
    leakage_risk: None (historic)
    data_quality_reqs:
      - Default to fleet median (91.0%) for gateways missing from file
    explainability: "Historic data collection reliability is N%"
    decision: EXPLANATION

  - id: F08_DISCONNECTION_FREQUENCY
    name: Socket Disconnection Count
    definition: Total number of connection drop events during the week
    source: telemetry/*.parquet (disconnection_cnt)
    window: Trailing 7 days [T_eval - 7d, T_eval)
    formula: "SUM(disconnection_cnt)"
    availability: 100% of telemetry-reporting gateways
    leakage_risk: None
    data_quality_reqs:
      - Highly collinear with F01 (r = 0.94)
    explainability: "Device disconnected and reconnected N times"
    decision: EXPLANATION

  - id: F09_RADIO_SIGNAL_STRENGTH
    name: Weak Radio Signal Metric
    definition: Counters of weak RSSI/RSRP
    source: telemetry/*.parquet (rssi_bad, rscp_rsrp_bad)
    window: Trailing 7 days
    formula: "SUM(rssi_bad + rscp_rsrp_bad)"
    availability: 100%
    leakage_risk: None
    data_quality_reqs: None
    explainability: "Weak cellular reception counter"
    decision: REJECTED
    rejection_rationale: "Field visits dispatched for weak signal had 0.0% repair success (0 of 79 fixed). Causes unnecessary truck rolls (€380 waste) where no defect is found."

  - id: F10_CPU_LOAD_ANOMALIES
    name: CPU System Load
    definition: System load average exceeding threshold
    source: telemetry/*.parquet (avg_load1, load1_bigger2)
    window: Trailing 7 days
    formula: "SUM(load1_bigger2)"
    availability: 100%
    leakage_risk: None
    data_quality_reqs: None
    explainability: "Processor load spikes"
    decision: REJECTED
    rejection_rationale: "99.3% of records are zero; shows negligible correlation with hardware defects or connection outages."

  - id: F11_HISTORICAL_FIELD_VISITS
    name: Past Field Visit History
    definition: Count of previous technician visits
    source: field_visits.csv
    window: 2025-02 to 2026-02
    formula: "COUNT(visit_id)"
    availability: 247 gateways
    leakage_risk: High (introduces severe selection bias into ranking)
    data_quality_reqs: None
    explainability: "Gateway visited N times in past"
    decision: REJECTED
    rejection_rationale: "Using past visit count creates a self-fulfilling loop. Technicians repeatedly revisit known locations (60.7% finding nothing). Must rank based on physical failure telemetry."
```

---

## 2. Ingestion & Join Contract

```
[gateway_master.csv]           [telemetry/*.parquet]
       │                                │
       ▼ (REPLACE ':', '')              ▼ (native 12-char hex)
[gateway_id_norm] ─────────────▶ [gateway_id_norm]
       │
       ├─ n_meters_installed ───────────▶ [Feature F05]
       └─ decommissioned_on  ───────────▶ [Filter: exclude if decommissioned]
```

---

## 3. Operational Fallback & Missing Data Rules

1. **Gateways with Zero Telemetry in Scoring Window**:
   - If `installed_on > T_eval`: Exclude completely.
   - If `decommissioned_on < T_eval`: Exclude completely.
   - If actively installed but 0 telemetry received: Treat as **100% hard dead** ($C_{\text{offline\_max}} = 168\text{h}$, priority elevated).
2. **Cold-Start Gateways ($< 336$ Hours History)**:
   - Substitute fleet-wide median baseline parameters: $\mu_{\text{fleet}}, \sigma_{\text{fleet}}$.
   - Mark in response payload: `data_confidence = "LOW_HISTORY"`.
3. **Missing Installed Meters**:
   - If `n_meters_installed` is null: Impute fleet median (156 meters).
