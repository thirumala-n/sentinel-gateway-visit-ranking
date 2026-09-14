# Sentinel — Feature Evidence & Evaluation Specification (Phase 2)

## Overview

In accordance with competition guidelines, features are evaluated strictly on empirical evidence from the NEXORA challenge datasets. We reject feature inflation; the objective is **fewer strong, explainable, evidence-backed features** rather than a dense vector of noisy metrics.

---

## 1. Candidate Feature Evidence Audit

Each feature candidate is scrutinized across eight standard operational criteria:
1. **Measurement Target**: Physical or computational phenomenon captured.
2. **Operational Value**: How it aligns with the €380 false-visit cost vs. €600/week dead-gateway penalty.
3. **Empirical Support**: Measured behavior in actual telemetry, field visits, and meter reads.
4. **Redundancy Risk**: Overlap with other candidate metrics.
5. **Outlier Vulnerability**: Sensitivity to extreme spikes or recording noise.
6. **Leakage Safety**: Computability strictly prior to prediction Monday 00:00:00 UTC.
7. **Managerial Explainability**: Intuitive transparency for field dispatchers.
8. **Final Decision**: `PRIMARY`, `SECONDARY`, `EXPLANATION`, or `REJECTED`.

---

### Group A: Anomaly Frequency
#### A1. Anomaly Flagged Hours ($F_{\text{anom}}$)
- **Measurement**: Count of hours in the 7-day scoring window exceeding $\mu + 3\sigma$.
- **Operational Value**: Captures recurring instability across the scored period.
- **Empirical Support**: Strong; top 10 gateways exhibit 50–135 flagged hours (see `BASELINE_ANALYSIS.md`).
- **Redundancy Risk**: High if summed across collinearly coupled columns (`offline_duration` + `disconnection_cnt`). Must be computed over deduplicated metric groups.
- **Outlier Vulnerability**: Low (bounded by $0 \le F \le 168$).
- **Leakage Safety**: 100% safe (strictly within trailing 7-day scoring window).
- **Explainability**: High ("Gateway exceeded normal operating envelope for X hours this week").
- **Decision**: **PRIMARY CANDIDATE**

---

### Group B: Anomaly Magnitude
#### B1. Cumulative Excess Z-Score ($Z_{\text{excess}}$)
- **Measurement**: $\sum_{t \in W_S} \max(0, \frac{X_t - \mu}{\sigma_{\text{eff}}} - 3.0)$.
- **Operational Value**: Differentiates a borderline blip ($z = 3.1$) from an overwhelming total collapse ($z = 25.0$).
- **Empirical Support**: Prevents ties among gateways with identical flag counts.
- **Redundancy Risk**: Moderately correlated with flagged hours ($r \approx 0.78$), but adds critical vertical depth.
- **Outlier Vulnerability**: Moderate; requires capping $z \le 10$ to prevent single glitch domination.
- **Leakage Safety**: 100% safe.
- **Explainability**: Medium ("Severity of deviation above threshold").
- **Decision**: **SECONDARY CANDIDATE** (used for tie-breaking and severity weighting).

---

### Group C: Persistence
#### C1. Maximum Consecutive Offline Run ($C_{\text{offline\_max}}$)
- **Measurement**: Longest uninterrupted sequence of hours where `offline_duration_sec` $\ge 3000$ seconds.
- **Operational Value**: Directly pinpoints dead gateways. A 24-hour continuous outage represents a sustained hard failure where leaving the gateway unvisited incurs €600/week.
- **Empirical Support**: In `field_visits.csv`, reasons citing `Keine Verbindung` achieved the highest repair rate (**65.0%**), replacing blown power supplies and antennas.
- **Redundancy Risk**: Low; orthogonal to intermittent transient counts.
- **Outlier Vulnerability**: Low (bounded $0 \le C \le 168$).
- **Leakage Safety**: 100% safe.
- **Explainability**: Very High ("Gateway was completely unreachable for X consecutive hours").
- **Decision**: **PRIMARY CANDIDATE**

---

### Group D: Trend & Trajectory
#### D1. Trajectory Delta Ratio ($\Delta_{\text{trend}}$)
- **Measurement**: $\frac{\text{Recent } 72\text{h Offline Duration}}{\text{Previous } 96\text{h Offline Duration} + \epsilon}$.
- **Operational Value**: Prevents unnecessary dispatches to self-healed gateways. If a gateway failed on Day 1 but recovered by Day 3, dispatching a technician on Day 8 wastes €380. If failure is accelerating, dispatch is urgent.
- **Empirical Support**: In `phase2_deep_analysis.txt`, gateways like `065B928716CD` surged from 113,839 sec to 202,221 sec offline (+77% jump).
- **Redundancy Risk**: Low; captures temporal derivative.
- **Outlier Vulnerability**: Low when framed as bounded ratio.
- **Leakage Safety**: 100% safe.
- **Explainability**: High ("Is the gateway failure worsening or recovering?").
- **Decision**: **PRIMARY CANDIDATE**

---

### Group E: Connectivity
#### E1. Hourly Offline Duration (`offline_duration_sec`)
- **Measurement**: Seconds disconnected from server per hour.
- **Operational Value**: Core measure of availability.
- **Empirical Support**: Directly tracks disconnection severity.
- **Decision**: **PRIMARY CANDIDATE** (Input to Frequency, Persistence, and Trend).

#### E2. Disconnection Count (`disconnection_cnt`)
- **Measurement**: Number of socket drops/reconnects per hour.
- **Operational Value**: Indicates cellular flapping.
- **Empirical Support**: Highly correlated with `offline_duration_sec` ($r = 0.94$).
- **Decision**: **EXPLANATION ONLY** (Do not duplicate in composite score to avoid double-weighting).

---

### Group F: Reboot Behavior
#### F1. Hourly Reboot Frequency (`reboot_cnt`)
- **Measurement**: Number of device restarts per hour.
- **Operational Value**: Hardware or power instability. In `field_visits.csv`, `Haeufige Neustarts` had a **56.4% true positive rate** with power supply (`Netzteil`) and gateway swaps.
- **Empirical Support**: Non-zero in only 1.6% of hours; when present, it is a high-confidence indicator of physical distress.
- **Decision**: **PRIMARY CANDIDATE**

---

### Group G: Radio Quality
#### G1. Signal Quality Metrics (`rssi_bad`, `rscp_rsrp_bad`, `ecio_rsrq_bad`)
- **Measurement**: Low cellular reception signal counters.
- **Operational Value**: Poor reception.
- **Empirical Ground-Truth Finding**: In `field_visits.csv`, **0 out of 79 field visits** dispatched for `Signal schwach` resolved any fault (73 Kein Fehler, 6 Kein Zugang — **0.0% resolution rate**). Weak signal is a static environmental attribute of basement/thick-walled installations, not a serviceable malfunction.
- **Redundancy / Harm**: Dispatching technicians based on radio signal is the single largest cause of wasted truck rolls (€380 each).
- **Decision**: **REJECTED** (Must NOT be included in ranking formula; retained solely for UI context/explanation).

---

### Group H: System Resources
#### H1. Load & Memory (`avg_load1`, `load1_bigger2`, `avg_memfree`)
- **Measurement**: CPU load averages and free RAM.
- **Empirical Finding**: 99.3% of `load1_bigger2` records are 0.0. Average memory free shows virtually no correlation with connection drops or field repairs.
- **Decision**: **REJECTED** for scoring; retain as **EXPLANATION ONLY**.

---

### Group I: Customer / Meter Exposure
#### I1. Number of Installed Meters (`n_meters_installed`)
- **Measurement**: Customer meters dependent on the gateway (from `gateway_master.csv`). Range: 40 to 822 meters.
- **Operational Value**: Direct business impact multiplier. A broken gateway serving 822 meters causes 20x more billing and customer damage than one serving 40 meters.
- **Empirical Support**: 100% available across all 320 active gateways.
- **Leakage Safety**: Master metadata known at all times.
- **Explainability**: Absolute ("Gateway serves 822 customer meters").
- **Decision**: **PRIMARY CANDIDATE** (Business Impact Multiplier).

---

### Group J: Historical Reliability & Meter Success
#### J1. Historic Meter Read Success Rate (`success_rate_pct`)
- **Measurement**: $100 \times \frac{\text{meters\_read}}{\text{meters\_expected}}$ from `meter_read_success.csv`.
- **Operational Value**: Direct business proof of data loss.
- **Empirical Limitation**: Stale by 5 weeks for March 2026 scoring.
- **Decision**: **EXPLANATION & TIE-BREAKER ONLY** (Cannot be real-time primary signal due to 5-week lag).

---

## 2. Temporal Leakage Defense & Cutoff Boundary

To ensure zero future data leakage:
- For any target prediction week with Monday start date $T_{\text{target}}$ (e.g. `2026-03-09 00:00:00 UTC`):
  1. **Strict Upper Bound**: No record with timestamp $\ge T_{\text{target}}$ may be loaded or queried.
  2. **Baseline Window**: Trailing 28 calendar days strictly before target Monday: $[T_{\text{target}} - 28\text{ days}, T_{\text{target}})$.
  3. **Scoring Window**: Trailing 7 calendar days strictly before target Monday: $[T_{\text{target}} - 7\text{ days}, T_{\text{target}})$.
  4. **Relationship**: The recent 7-day scoring window is the tail end of the 28-day historical period and is not excluded from baseline parameter calculations. Both windows are strictly past observations relative to $T_{\text{target}}$.
  5. **Field Visits & Reviews**: No historical visit or review outcome post-dating $T_{\text{target}}$ can be referenced. In fact, historical field visits are **completely excluded** from the online scoring formula to avoid retrospective selection bias.

---

## 3. Recommended Feature Set for Phase 3

We recommend a parsimonious, 4-dimensional composite ranking architecture:

$$\text{Priority Score} = \underbrace{\Big( w_1 \cdot F_{\text{anom}}^{\text{norm}} + w_2 \cdot C_{\text{offline}}^{\text{norm}} + w_3 \cdot R_{\text{reboot}}^{\text{norm}} \Big)}_{\text{Technical Failure Severity}} \times \underbrace{\Big( 1 + \alpha \cdot \text{Trend}_{\text{urgency}} \Big)}_{\text{Trajectory Adjustment}} \times \underbrace{\Big( \frac{n_{\text{meters}}}{\bar{n}_{\text{meters}}} \Big)^\beta}_{\text{Business Impact Multiplier}}$$

> [!NOTE]
> *Status on Weights*: In accordance with Section 14 of Phase 2 guidelines: **WEIGHTS NOT YET JUSTIFIED**. The mathematical structure is specified and evidence-backed, but exact numerical hyperparameters ($\alpha, \beta, w_i$) will be calibrated in Phase 3 through cross-validation against the €380 / €600 economic loss function.
