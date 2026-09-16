# Sentinel — Engineering & Architectural Decisions (25% Judgement)

This document records the foundational judgements, architectural tradeoffs, empirical evidence, and boundary limitations governing project **Sentinel**.

Per NEXORA guidelines, each decision is formulated not as a chronological development log, but as an explicit evaluation of conclusions, rejected alternatives, operational risks, and falsification criteria.

---

## The Five Required Decisions

The brief asks for five choices, each with the alternative considered and why it was rejected, with one of the five naming the chosen Part 2 area. Those five are:

1. **Decision 1** — Problem Definition: what "needs a visit" means.
2. **Decision 2** — Ranking Methodology: staged risk scoring vs. alternatives.
3. **Decision 5** — Temporal Anti-Leakage Protocol.
4. **Decision 9** — Architecture, In-Memory Caching & Zero-Restart Rerun.
5. **Decision 10** — Part 2 Specialization: **Software Development**, and why.

Decisions 3, 4, 6, 7, and 8 are additional supporting technical records kept for completeness and for the live session, not part of the required five above.

---

## Decision 1: Problem Definition — What "Needs a Visit" Means (Detecting Existing Faults vs Predicting Future Faults)

### Decision
Define "needs a visit" as **demonstrated, ongoing, actionable physical distress** that cannot resolve without on-site technician intervention (power supply failure, antenna degradation, modem lockup, cable cut). Sentinel optimizes dispatch capacity (15 visits/week) to remediate active or accelerating multi-day outages, rather than attempting predictive early-warning forecasting of healthy units that might theoretically degrade in future weeks.

### Why We Chose It
In smart-grid operations, a gateway experiencing an ongoing complete outage ($F_{03} \ge 24\text{h}$) incurs a known, certain €600 weekly loss and disrupts customer billing reads immediately. Predictive forecasting on noisy, healthy gateways has low precision and high false-positive rates; rolling a €380 truck to a currently working gateway based on subtle noise leads to technician distrust and wasted budget.

### Alternative Considered
A predictive failure forecasting model (e.g. predicting which currently healthy gateway will fail within the next 14 days based on subtle micro-disconnects or RSSI variance).

### Why Alternative Was Rejected
Analysis of `field_visits.csv` (642 historical work orders) revealed that 60.7% of past technician visits ended with `Kein Fehler gefunden` (no defect found). This reflects high operational uncertainty in historical dispatch practices rather than proof that the initial alert was false. Adding a predictive model with inherently higher uncertainty would exacerbate wasted truck rolls. Resolving deterministic, active outages provides immediate, provable operational value.

### Evidence / Test Supporting the Choice
In historical backtesting across 11 validation weeks (`BACKTESTING.md`), prioritizing confirmed multi-hour outages ($F_{03}$) and reboot distress ($F_{02}$) increased physical defect resolution (`Fehler behoben`) by **+53.3%** over the reference baseline, capturing 23 confirmed hardware repairs vs 15. This is evidence from historical validation, not a claim of realized financial savings.

### What Could Go Wrong
A gateway could suffer sudden catastrophic failure on Monday afternoon immediately after dispatch decisions are locked at Monday 00:00 UTC, leaving that gateway unaddressed until the subsequent week.

### What Would Change Our Mind
If field operations adopted daily dynamic dispatching (intraday re-routing) rather than fixed weekly batches, or if high-frequency sensor telemetry (e.g. internal capacitor voltage, thermal telemetry) were added that exhibited strong pre-failure physical precursors.

---

## Decision 2: Ranking Methodology — Staged Risk Optimization with Logarithmic Impact Multiplier

### Decision
Adopt a staged formula combining an additive technical failure core ($R_{\text{tech}}$) with a guarded logarithmic customer impact multiplier ($M_{\text{impact}}$):
$$R_{\text{tech}} = (0.45 \cdot \tilde{F}_{01} + 0.35 \cdot \tilde{F}_{03} + 0.20 \cdot \tilde{F}_{02}) \times T_{\text{trend}}$$
$$\text{If } R_{\text{tech}} \ge 0.05: \quad \text{Score} = R_{\text{tech}} \times \frac{\log_{10}(\max(10, F_{05}))}{\log_{10}(156)} \times 100.0$$
$$\text{If } R_{\text{tech}} < 0.05: \quad \text{Score} = R_{\text{tech}} \quad (\text{fallback slot, unmultiplied})$$

### Why We Chose It
The weekly 15-visit budget must resolve the most severe technical failures while accounting for customer exposure (installed meters range from 40 to 822; fleet median: 156). An additive technical core prevents zero-collapsing (where an anomaly in one dimension is cancelled by zero in another), while the guarded logarithmic multiplier allows customer blast radius to break ties and elevate high-impact sites without ever allowing healthy high-meter sites to displace degraded units.

### Alternative Considered
1. *Unweighted 3-sigma anomaly counting* (the competition reference baseline).
2. *Pure multiplicative scoring* ($\text{Score} = \text{Anomalies} \times \text{Persistence} \times \text{Meters}$).
3. *Linear additive weighting* ($w_1 F_{01} + w_2 F_{03} + w_3 F_{05}$).

### Why Alternative Was Rejected
- *Unweighted baseline* treats 15 isolated 1-minute flaps identically to 15 hours of solid dead carrier, completely ignoring persistence, trajectory, and blast radius.
- *Pure multiplicative* collapses to 0 whenever a single metric is 0, ignoring severe reboot loops that have low offline time.
- *Linear additive weighting* suffers from meter count inflation: a healthy gateway with 800 meters and 1 transient blip would score higher than a dead gateway with 50 meters, violating physical dispatch necessity.

### Evidence / Test Supporting the Choice
- `RiskBasedRankingStrategyTest`: Proves healthy gateways with 822 meters receive scores $< 0.05$ and are marked as fallback.
- `HistoricalBacktestIntegrationTest`: Proves dead gateway `02D3289B907C` (168h outage) ranks #1 with score $> 80.0$, demonstrating strong alignment with confirmed physical failures.

### What Could Go Wrong
If installed meter counts in `gateway_master.csv` become obsolete or out of sync with actual operational topology, high-impact weighting could be marginally distorted.

### What Would Change Our Mind
If utility contract SLAs introduced uniform per-gateway penalties irrespective of connected meters, we would eliminate $M_{\text{impact}}$ entirely and rank purely on $R_{\text{tech}}$.

---

## Decision 3: Feature Selection & Collinear Metric Quarantine

### Decision
Select 5 primary features for the scoring core ($F_{01}$ offline anomaly hours, $F_{02}$ reboot anomaly hours, $F_{03}$ max consecutive offline hours, $F_{04}$ trend trajectory ratio, $F_{05}$ installed meters exposure) and strictly quarantine redundant metrics ($F_{08}$ disconnection count, $F_{06}$ excess z-score, $F_{07}$ meter read success) to secondary explanatory roles.

### Why We Chose It
Empirical analysis of 1,433,387 telemetry records revealed extreme multicollinearity between `disconnection_cnt` and `offline_duration_sec` ($r = 0.94$). Every socket drop generated redundant statistical anomalies across offline duration, disconnect count, and connection importance. Using only orthogonal physical indicators produces a stable, interpretable ranking.

### Alternative Considered
Including all 58 available telemetry columns and auxiliary dataset metrics into a high-dimensional feature vector or composite score.

### Why Alternative Was Rejected
- Zero-variance operator columns (`operator_3AT`, `operator_OrangeLU`, etc.) contain 0 active hours across Germany and add noise.
- `disconnection_cnt` artificially triple-counts cellular reconnection flapping, elevating noisy cellular towers over genuine hardware failures.
- `meter_read_success.csv` ends on 2026-01-26 (DQ-07); using it as an active scoring feature in March 2026 would inject a 5-week temporal lag.

### Evidence / Test Supporting the Choice
- `F01F02FlaggedHoursTest`: Verifies statistical isolation of offline and reboot hours.
- `F04TrendTrajectoryTest`: Proves $F_{04}$ distinguishes accelerating failures ($T_{\text{trend}} = 1.2$) from self-healing recoveries ($T_{\text{trend}} = 0.8$).

### What Could Go Wrong
A rare hardware defect characterized exclusively by repeated high-frequency cellular disconnects without offline accumulation could be scored lower than a continuous disconnect.

### What Would Change Our Mind
If field evidence proved that high disconnect frequency without offline duration caused widespread modem hardware burnouts requiring physical replacement.

---

## Decision 4: Machine Learning Avoidance — Deterministic Fixed-Weight Scoring vs Supervised ML

### Decision
Implement a deterministic, formula-driven scoring engine with fixed engineering weights evaluated via walk-forward historical backtesting rather than training a supervised machine learning model (e.g. XGBoost, Random Forest, Neural Network).

### Why We Chose It
1. **Label scarcity and bias**: `field_visits.csv` contains only 642 rows across 12 months, where 60.7% of past technician visits resulted in no defect being found. Training ML on historical dispatch decisions simply risks teaching the model to replicate past operator dispatch noise.
2. **Explainability mandate**: The NEXORA specification requires explaining *why* a gateway is ranked where it is. A deterministic formula provides 100% transparent, feature-traceable rationales with zero hallucination.
3. **Auditability & Stability**: Supervised ML models are prone to silent distribution drift when new telemetry partitions arrive, whereas our staged formula provides guaranteed monotonic behavior.

### Alternative Considered
Training a gradient-boosted decision tree (XGBoost / LightGBM) predicting binary visit repair outcome (`outcome == 'Fehler behoben'`).

### Why Alternative Was Rejected
- Extreme class imbalance and observational bias: outcomes only exist for gateways humans chose to visit; unvisited failing gateways have no labels (survival bias).
- Fragility on unseen data: in Round Two, evaluators drop unseen telemetry into the system; ML models trained on small historical samples risk severe overfitting on partition boundaries.

### Evidence / Test Supporting the Choice
- `HistoricalBacktestIntegrationTest`: Fixed-weight formula achieves 46.7% repair conversion in high-incident weeks without any training phase.
- `ExplanationDeterminismTest`: Proves identical feature inputs generate byte-for-byte identical explanations and rankings across independent runs.

### What Could Go Wrong
Complex nonlinear interactions between multiple low-level signals that a human engineer would not anticipate could be missed by a structured 5-feature formula.

### What Would Change Our Mind
If LPDG provided a dataset of 50,000+ verified, randomized field inspections with complete ground-truth sensor telemetry and confirmed failure root causes.

---

## Decision 5: Ground Truth Interpretation & Strict Temporal Anti-Leakage Protocol

### Decision
Treat historical field visits (`field_visits.csv`) and engineer audits (`engineer_review_2026-02.xlsx`) strictly as **post-hoc validation evidence**, NEVER as inputs to the runtime prediction pipeline. Enforce a strict temporal cutoff: for any prediction week anchored at Monday $T_{\text{target}}$ 00:00:00 UTC, no observation with timestamp $\ge T_{\text{target}}$ can be accessed.

### Why We Chose It
In competitive evaluation and production deployment, future data leakage disqualifies the system. `engineer_review_2026-02.xlsx` was conducted on 2026-02-14. Allowing review findings to inform rankings prior to 2026-02-14 would constitute catastrophic future leakage.

### Alternative Considered
Using the 120 expert labels (`Schlecht` / `Normal`) from the engineer review as prior probabilities or feature inputs for all weeks in 2025 and 2026.

### Why Alternative Was Rejected
The engineer review represents an audit of gateway status *as of mid-February 2026*. A gateway rated `Schlecht` in February may have been completely healthy in September 2025; using February knowledge in September is invalid temporal back-propagation.

### Evidence / Test Supporting the Choice
- `TemporalLeakageTest`: Verifies that timestamps $\ge T_{\text{target}}$ are strictly excluded at both query and feature extraction boundaries (`scoringEnd == targetMonday`, exclusive).
- `git grep "engineer_review" src/main`: Returns zero code references (only documentation comments), confirming zero runtime pipeline leakage.

### What Could Go Wrong
If an evaluator attempts to generate predictions for a target week prior to 2025-09-01 (before 28 days of telemetry exist), the system must reject the request with HTTP 422 rather than producing incomplete baseline statistics.

### What Would Change Our Mind
Nothing. Temporal barriers are a non-negotiable physical law of valid software engineering.

---

## Decision 6: Cost Formulation & Economic Distinction

### Decision
Strictly distinguish between **official competition scoring economics** and **Sentinel's internal operational prioritization model**:
- **Official Competition Scoring**: €380 per dispatched technician visit + €600 per unattended faulty gateway per week. The official penalty applies uniformly per faulty gateway and is **NOT multiplied by installed meter count**.
- **Sentinel Internal Prioritization**: Weight technical failure risk by installed meter count ($M_{\text{impact}}$) strictly for internal rank ordering among degraded units, ensuring scarce technician capacity addresses high-blast-radius failures first.

### Why We Chose It
Falsely claiming that customer meter count changes the official €600 scoring cost would be an economic inaccuracy. However, in real utility operations, leaving 800 customers without smart meter data is operationally worse than leaving 40 customers without data. Using meter count internally for rank ordering maximizes real-world utility while respecting the official scoring rules.

### Alternative Considered
Claiming in documentation and code that Sentinel saves €600 $\times$ meters in competition scoring.

### Why Alternative Was Rejected
The NEXORA challenge rules specify €600 per gateway, not per meter. Making inflated cost claims reduces evaluator trust and demonstrates poor operational judgement.

### Evidence / Test Supporting the Choice
- `BACKTESTING.md` Section 4 explicitly documents the official competition economics (€380 per visit, €600 per unvisited faulty gateway per week, NOT multiplied by meter count). Across 11 historical walk-forward validation weeks, Sentinel selected 23 gateways associated with confirmed repairs versus 15 selected by the baseline (+53.3%). This is evidence from historical validation, not a claim of realized financial savings.

### What Could Go Wrong
An evaluator might confuse the internal logarithmic impact multiplier with the official scoring formula if documentation does not explicitly separate them.

### What Would Change Our Mind
If the competition rules explicitly introduced meter-weighted SLA penalty tiers.

---

## Decision 7: Missing Telemetry & Cold-Start Policy

### Decision
Treat active gateways with **zero telemetry** in the scoring week as `DataConfidence.MISSING_TELEMETRY` with maximal technical urgency ($R_{\text{tech}} = 1.0$), score $100.0 \times M_{\text{impact}}$, and an explicit decision rationale: `No telemetry received in scoring week; communications failure cannot be excluded`. For cold-start gateways ($< 336\text{h}$ baseline history), enforce robust baseline variance floors ($\sigma_{\min} = 60\text{s}, \sigma_{\min} = 0.5$).

### Why We Chose It
A gateway that transmits 0 records over 7 consecutive days has suffered a total communications or power blackout. Treating 0 records as 0 offline hours (the SQL default for missing rows) would misclassify a completely dead gateway as 100% healthy.

### Alternative Considered
1. Imputing missing telemetry with fleet averages.
2. Dropping missing-telemetry gateways from ranking consideration.

### Why Alternative Was Rejected
- Imputing fleet averages conceals total outages.
- Dropping uncommunicative gateways creates a catastrophic blind spot: the most broken gateways would never be visited!

### Evidence / Test Supporting the Choice
- `ADR-012` and `BaselineRankingStrategyTest`: Verifies missing telemetry handling.
- Historical validation: Dead gateway `02D3289B907C` (168h dead) ranked #1 in week 2026-03-09.

### What Could Go Wrong
If an active gateway is decommissioned in the field but the central ERP database (`gateway_master.csv`) fails to update `decommissioned_on`, Sentinel will dispatch a technician to inspect a gateway that was intentionally removed.

### What Would Change Our Mind
If telemetry ingestion provided explicit out-of-band network ping confirmations distinguishing decommissioned units from hardware outages.

---

## Decision 8: Strict 5-Tier Deterministic Tie-Breaking Hierarchy

### Decision
Enforce a strict 5-tier deterministic comparator for all rankings:
1. `score` descending ($\epsilon = 10^{-6}$)
2. `f03MaxConsecutiveOfflineHours` descending
3. `f01FlaggedOfflineHours` descending
4. `f05MetersExposure` descending
5. `gatewayId` ascending (12-character canonical hex string)

### Why We Chose It
In automated scoring harnesses and live evaluations, non-deterministic tie-breaking causes different visit lists across runs, generating evaluation discrepancies and failing consistency checks.

### Alternative Considered
Relying on standard Java `Double.compare()` or SQL `ORDER BY score DESC` without secondary keys.

### Why Alternative Was Rejected
When multiple gateways receive identical scores (e.g. during quiet weeks or fallback fills), arbitrary JVM or database sort ordering produces different visit lists on repeated calls, breaking API/CSV idempotency.

### Evidence / Test Supporting the Choice
- `ExplanationDeterminismTest`: Proves byte-for-byte reproducibility across independent runs.
- `PredictionsCsvConsistencyTest`: Proves 100% rank, gateway ID, score, and reason agreement between REST API and exported CSV.

### What Could Go Wrong
None. A deterministic tie-breaker guarantees identical outputs for identical inputs.

### What Would Change Our Mind
Nothing. Idempotency is a mandatory software engineering requirement.

---

## Decision 9: Architecture, In-Memory Caching & Zero-Restart Dynamic Partition Rerun

### Decision
Implement a hexagonal (ports & adapters) architecture where DuckDB dynamically evaluates filesystem globs (`data/telemetry/*/*.parquet`) at query execution time, coupled with a per-week in-memory cache evicted on `POST /api/v1/predictions/{week}/rerun`.
The rerun endpoint recomputes predictions specifically for the targeted week by evicting its cache entry and re-executing DuckDB queries directly against `./data`.

### Why We Chose It
1. **Per-Week vs All-Weeks Scope**: Target-week rerun was chosen deliberately because field dispatch operations operate on an explicit weekly horizon ($T_{\text{target}}$). Recomputing only the active decision week provides instantaneous response times (<1.5s) during live evaluator interaction, avoids blocking the server with unrequested historical weeks, and provides clean temporal isolation.
2. **Zero-Restart Requirement**: NEXORA Round Two explicitly simulates live operations: new telemetry partitions are dropped into the mounted `data` directory while the application is already running, followed by a `/rerun` request. Because DuckDB scans the parquet glob dynamically on each execution, calling `/rerun` clears the cache and immediately ingests newly added partitions with **zero JVM restart required**.
3. **Idempotency & Concurrency**: Repeated rerun calls are safe and idempotent. Concurrent calls are thread-safe via `ConcurrentHashMap` and DuckDB read queries.

### Alternative Considered
- Static batch pre-loading on application startup into memory.
- Requiring `systemctl restart sentinel` or Docker container restart to pick up new partitions.
- Global rerun that recomputes all 8 weeks on every call.

### Why Alternative Was Rejected
- Requiring application restarts during a live demonstration creates operational friction, risks downtime, and fails the live session test criteria.
- Global all-week rerun would take ~12 seconds of blocking analytical compute, delaying the live session feedback loop when the evaluator is testing a change for one specific week.

### Evidence / Test Supporting the Choice
- `RerunDynamicDataIntegrationTest`: Proves that adding a new partition (`month=2026-03`) while the service is running is dynamically ingested on `rerun`, updating rankings from nominal to rank #1 failure without restarting the service.
- `PredictionRerunDeterminismTest`: Proves cache hit returns in $<1\text{ms}$ while rerun evicts and re-executes cleanly.

### What Could Go Wrong
If the host file system encounters read locking during an ongoing partial file write by an external ingestion process, DuckDB could encounter a transient read error. (Mitigated by writing to temp files and atomically renaming).

### What Would Change Our Mind
If dataset volume exceeded single-machine storage capacity (e.g. >10 TB), requiring distributed query engines (Trino / Spark).

---

## Decision 10: Selected Part 2 Specialization — Why Software Development (60%)

### Decision
Select **Software Development** as our primary Part 2 evaluation area (60% weight), optimizing for robust architecture, test coverage (213 automated tests, ArchUnit, E2E), clean strategy substitution, deliberate error contracts (RFC-7807), and live session change readiness.

### Why We Chose It
The operational reality of utility field operations requires dependable, maintainable, resilient services. A complex machine learning model that crashes on malformed inputs or requires hours to retrain provides zero operational value. Software Development allows us to deliver:
1. Rock-solid reliability and clean-machine reproducibility.
2. Complete API contracts with explainability and pairwise comparison.
3. Live session agility: ability to modify the API under live evaluator inspection within minutes.

### Alternative Considered
Selecting Data Science or Business Analytics as the 60% area.

### Why Alternative Was Rejected
Data Science without solid engineering produces brittle prototypes that fail on unseen data or struggle under live change requests. By anchoring in Software Development, we demonstrate both strong analytical methodology (empirical backtesting, feature selection) and professional-grade engineering execution.

### Evidence / Test Supporting the Choice
- 213 automated tests passing with 0 failures, 0 errors (193 test definitions across 28 test classes, including 5 ArchUnit rules and repeated determinism runs).
- 5/5 ArchUnit architectural layer enforcement rules.
- Complete RFC-7807 error contracts (400 `MALFORMED_WEEK`, 400 `INVALID_WEEK_DAY`, 404 `GATEWAY_NOT_FOUND`, 422 `UNSUPPORTED_WEEK`, 422 `FUTURE_WEEK`).
- Clean strategy substitution between `RiskBasedRankingStrategy` and `BaselineRankingStrategy` with zero controller changes.

### What Could Go Wrong
An evaluator with an exclusive ML bias might penalize the submission for avoiding deep learning or neural models.

### What Would Change Our Mind
Nothing. Grounding operational systems in sound software engineering principles is the correct staff-level engineering decision.
