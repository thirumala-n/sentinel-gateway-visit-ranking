# Sentinel — Known Limitations & Operational Constraints

This document outlines the operational boundaries, known limitations, and non-goals of project **Sentinel**.

---

## 1. Operational Reality — What Sentinel Cannot Do

In field operations, data telemetry provides strong evidence but cannot capture every physical reality. Evaluators and dispatch operators should be aware of the following concrete boundaries:

1. **Telemetry & Data-Pipeline Outage Resemblance:**
   An upstream failure in data ingestion (e.g. broker disconnect, cellular gateway collector downtime, corrupted ingestion pipeline) produces an absence of records ($F_{03} = 48\text{h}$ or `MISSING_TELEMETRY`). Sentinel detects that telemetry is missing, but cannot definitively distinguish an ingestion pipeline outage from a physical gateway failure without out-of-band monitoring.

2. **Facility-Wide Power & Regional Cellular Mast Failures:**
   If an entire municipal facility experiences a mains power cut or a regional cellular base transceiver station (BTS) fails, all gateways in that geographic cluster go offline simultaneously. Dispatching 10 technicians to individual gateways in the same sector would waste operational budget on issues that require a facility electrician or telecom provider intervention.

3. **Unlabeled & Unvisited Gateways Are Not Automatically Healthy:**
   Technician capacity is strictly capped at 15 visits per week. The remaining ~285 gateways in the fleet are unvisited and unlabeled in historical logs. The absence of a technician ticket does **not** prove a gateway is healthy; unvisited gateways simply fell outside the top-15 capacity window.

4. **Historical Field Visits Suffer Strong Observational & Selection Bias:**
   In `field_visits.csv` (642 historical records), 60.7% of visits resulted in no defect being found (`Kein Fehler gefunden`). This indicates high operational uncertainty in past human dispatching practices, rather than proof that the initial operational anomaly was false. Historical visit logs reflect where past operators chose to look, not an unbiased random trial.

5. **Meter-Read Success Historical Cutoff & Ingestion Lag:**
   The auxiliary meter readings file (`meter_read_success.csv`) ends on 2026-01-26. When scoring March 2026 weeks, this data carries a 5-week lag. Consequently, meter reading success cannot be used as an active runtime scoring feature without introducing stale or leaked observations; it is strictly quarantined to descriptive context ($F_{07}$).

6. **Deterministic Decision Support vs. Physical Proof:**
   Sentinel is an operational **decision-support tool** that optimizes the weekly 15-visit budget using multi-dimensional telemetry evidence. It does not provide absolute physical proof of hardware failure prior to physical on-site inspection.

---

## 2. Technical & Architectural Constraints

### Architecture & Operational Constraints
- **Single-node DuckDB engine:** DuckDB is an in-process analytical OLAP engine. It is optimized for single-machine execution and does not support distributed clustering across multiple physical nodes.
- **Batch-oriented execution:** Features and baseline statistics are computed on weekly batch boundaries (every Monday 00:00 UTC), not in continuous streaming millisecond intervals.
- **Read-only input contract:** Sentinel strictly reads parquet telemetry and CSV files from `./data` and never mutates source datasets.
- **No online weight adaptation:** Fixed engineering weights are evaluated via walk-forward historical backtesting; the service does not alter scoring weights dynamically in production without explicit configuration changes.

### Explainability Constraints
- **Structured rule-based rationales:** Explanations are generated through deterministic structured templates ($F_{01}–F_{05}$ attributions) rather than generative LLMs. This guarantees byte-for-byte reproducibility and eliminates hallucinations, but does not provide free-form conversational interaction.
- **Static severity thresholds:** Severity categories (`CRITICAL`, `HIGH`, `MODERATE`, `LOW`) are anchored to fixed operational benchmarks ($84\text{h}$ offline, $48\text{h}$ continuous outage). Significant structural shifts in fleet size or reporting intervals would warrant reviewing these bounds.

### API & Concurrency Scope
- **Synchronous recomputation on `/rerun`:** `POST /api/v1/predictions/{week}/rerun` recomputes the target week synchronously in ~1.5 seconds. Burst concurrent reruns on the exact same week execute sequentially on DuckDB.
- **Unauthenticated REST API:** Per NEXORA competition guidelines, the service provides open REST endpoints without API keys or OAuth authentication. Production deployment requires placing Sentinel behind a TLS-terminating reverse proxy.

---

## 3. Out of Scope

- Distributed stream processing (Kafka/Flink/Spark)
- Multi-tenant cloud SaaS authentication & user billing
- Database migration writes back to operational SCADA systems
- Heavy frontend build toolchains (React/Next.js/npm; Sentinel uses a zero-dependency static HTML/CSS/JS console served by Spring Boot)

