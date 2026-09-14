# Sentinel Ranking Strategy Specification — Phase 4

## 1. Operational Decision Problem & Economic Objective

In municipal utility and smart-grid operations (LPDG × RGM), field technicians are a scarce, expensive operational resource.
The weekly dispatch constraints and unit economics are:

- **Weekly Capacity Constraint**: Exactly **15 site visits per week** maximum.
- **Unnecessary Visit Cost**: **€380 per wasted truck roll** (technician dispatched to a healthy or self-healed gateway where `Kein Fehler gefunden` is determined).
- **Unvisited Broken Gateway Cost**: **€600 per week** (meter billing data lost, SLA penalties, repeated weekly while failure persists).
- **Compounding Risk**: An unresolved hardware/power failure ($F_{03} \ge 24\text{h}$) repeats its €600 penalty week after week until visited and repaired.

$$\text{Expected Operational Value of Visit} = P(\text{Defective} \mid \text{Evidence}) \times €600 - €380$$

Therefore, the objective is **NOT** to maximize the count of raw statistical deviations. The objective is to **select the 15 gateways whose physical inspection yields the maximum expected operational value and risk mitigation**.

---

## 2. Evidence-Based Formula Derivation

### 2.1 Technical Risk ($R_{\text{tech}}$)
The technical failure intensity combines orthogonal signals validated in Phase 2:
$$R_{\text{tech}} = \left( 0.45 \cdot \tilde{F}_{01} + 0.35 \cdot \tilde{F}_{03} + 0.20 \cdot \tilde{F}_{02} \right) \times T_{\text{trend}}$$

Where:
- **$\tilde{F}_{01} = \min(1.0, F_{01} / 84.0)$**: Normalized Flagged Offline Hours. Saturated at 84 hours (3.5 days of outage) to prevent single-week runaway scale distortion.
- **$\tilde{F}_{03} = \min(1.0, F_{03} / 48.0)$**: Normalized Max Consecutive Offline Run. In `field_visits.csv`, complete connection loss (`Keine Verbindung`) yielded the highest repair rate (**65.0%**). An uninterrupted 48-hour run represents sustained, non-transient failure.
- **$\tilde{F}_{02} = \min(1.0, F_{02} / 24.0)$**: Normalized Flagged Reboot Hours. In `field_visits.csv`, frequent reboots (`Haeufige Neustarts`) achieved a **56.4% repair rate** (power supply and antenna replacements).
- **$T_{\text{trend}}$**: Trajectory acceleration multiplier from F04:
  - $T_{\text{trend}} = 1.2$ if $F_{04} > 1.2$ (failure accelerating immediately before the decision Monday).
  - $T_{\text{trend}} = 0.8$ if $F_{04} < 0.5$ (gateway is self-healing / recovering).
  - $T_{\text{trend}} = 1.0$ otherwise (stable trajectory).

### 2.2 Business Impact Scaling ($M_{\text{impact}}$)
Meters installed range from 40 to 822 (fleet median: 156).
A broken gateway serving 800 meters disrupts 5x more customer billing than one serving 40 meters.
However, **meter count must never push a healthy gateway into the top 15**.

- **Actionable Threshold Safeguard**:
  $$\text{If } R_{\text{tech}} < 0.05: \quad \text{Score} = R_{\text{tech}} \quad (\text{marked as fallback})$$
  A quiescent gateway with 822 meters receives a score $< 0.05$ and is never scheduled ahead of genuine failures.
- **Logarithmic Impact Multiplier** (for $R_{\text{tech}} \ge 0.05$):
  $$M_{\text{impact}} = \frac{\log_{10}(\max(10, F_{05}))}{\log_{10}(156)}$$
  *(Yields a smooth, well-conditioned multiplier in $[0.73, 1.33]$, moderating risk without overwhelming technical evidence).*

### 2.3 Composite Priority Score
$$\text{Score} = R_{\text{tech}} \times M_{\text{impact}} \times 100.0$$

---

## 3. Comparison with the Reference 3-Sigma Baseline

| Evaluation Dimension | Reference Baseline (`BaselineRankingStrategy`) | Competition Strategy (`RiskBasedRankingStrategy`) |
| :--- | :--- | :--- |
| **Scoring Formula** | $\sum \mathbb{I}(X > \mu + 3\sigma)$ (Unweighted flag count) | $R_{\text{tech}} \times M_{\text{impact}} \times 100$ |
| **Collinear Double-Counting** | Offline duration and disconnect count triple-counted | Only offline duration used; disconnect count quarantined for explanation |
| **Persistence Sensitivity** | Blind: 15 isolated 1-min blips equal 15h continuous dead | Separated: F03 ($35\%$ weight) strongly prioritizes continuous outages |
| **Directional Trend** | Blind: week-start outages equal week-end spikes | Dynamic: F04 damps recovering units (0.8x) and boosts accelerating units (1.2x) |
| **Zero-Variance Stability** | Fails: $\sigma = 0 \to$ single 5s blip triggers anomaly | Protected: Enforces $\sigma_{\min} = 60\text{ s}$ and $\sigma_{\min} = 0.5$ floors |
| **Business Impact** | 0% considered (treats 40 meters same as 822 meters) | Logarithmically scaled: prioritizes high-blast-radius failures |
| **Historical Repairs Captured**| **15 repairs** across 11 validation weeks | **23 repairs** (**+53.3% higher defect resolution**) |
| **Engineer `Schlecht` Capture**| **27 gateways** matched | **39 gateways** matched (**+44.4% higher expert agreement**) |

---

## 4. Operational Fallback & Missing Data Policies

1. **Missing Telemetry ($0\text{ h}$ received in scoring week)**:
   - For gateways confirmed active in master inventory (`installed_on \le T`, not decommissioned), receiving 0 telemetry indicates complete modem/power collapse.
   - Handled via `DataConfidence.MISSING_TELEMETRY`.
   - Score set to $100.0 \times M_{\text{impact}}$.
   - Rationale explicitly notes total communication outage.
2. **Low-Risk Fleet (Fallback Slots)**:
   - If fewer than 15 gateways exhibit active technical distress ($R_{\text{tech}} < 0.05$), remaining slots are populated using deterministic tie-breaking and marked with `fallback = true`.
   - Dispatchers receive clear warning: `Low operational risk; candidate included to fill 15-visit budget`.

---

## 5. Deterministic Tie-Breaking Hierarchy

The engine guarantees identical results across independent runs via a 5-tier deterministic comparator:
1. `score` descending ($\epsilon = 10^{-6}$)
2. `f03MaxConsecutiveOfflineHours` descending
3. `f01FlaggedOfflineHours` descending
4. `f05MetersExposure` descending
5. `gatewayId` ascending (canonical 12-char hex string)

---

## 6. Known Limitations & Edge Cases Where the Ranking Can Fail

1. **Cellular Carrier Tower Outages**: If a regional base station fails for 48 hours, all nearby gateways will exhibit high persistence ($F_{03} = 48$). Dispatching 10 technicians to individual gateways will waste budget because the fault lies with the cellular carrier, not the gateway hardware. *(Mitigated in Phase 5 via geographic correlation analysis).*
2. **Power Outage at Facility**: A building-wide power cut turns off the gateway and meters simultaneously. The gateway will show complete outage, but the issue requires a building electrician, not a gateway technician.
3. **Meter Expansion Latency**: `gateway_master.csv` installed meter counts are static snapshots; recent unrecorded meter additions are not captured until master inventory updates.
