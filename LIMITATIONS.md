# Sentinel — Known Limitations

This document lists known limitations, constraints, and out-of-scope items.

---

## Current Limitations

### Architecture & Operational Constraints

- **Single-node only.** DuckDB is an in-process analytical engine; horizontal scaling is not supported.
- **Batch-oriented.** The service processes telemetry and extracts features on a weekly batch basis (every Monday 00:00 UTC), not in real-time streaming mode.
- **Read-only input data.** The system strictly reads parquet telemetry, CSV meter readings, and inventory without mutating source files.
- **No active learning or online feedback loop.** Field technician repair outcomes are evaluated during offline historical backtesting; the ranking engine does not dynamically self-update weights in production without recalibration.

### Ranking & Feature Limitations

- **Heuristic composite model.** Ranking uses a calibrated multi-criteria scoring function ($R = R_{\text{tech}} \times M(m)$) rather than a black-box machine learning model, optimizing for determinism, interpretability, and auditability.
- **Baseline drift.** 28-day rolling baseline statistics adapt to seasonal fluctuations, but extended outages (>28 days) will eventually shift the baseline mean downward unless tracked across longer windows.
- **Meter reading granularity.** CSV meter readings represent sporadic counter reads rather than high-frequency telemetry; consumption anomaly features ($F_{08}$) have higher variance on newly deployed meters.

### Explainability Limitations (Phase 5)

- **Deterministic template explanations.** Explanations are generated via rule-based structured templates rather than LLM text generation. While eliminating hallucinations and guaranteeing reproducibility, explanations lack conversational flexibility.
- **Static severity thresholds.** Feature severity boundaries (e.g. F01 $\ge 42\text{h}$, F03 $\ge 24\text{h}$) are anchored to the ranking model's normalization denominators ($84\text{h}$, $48\text{h}$) established during the 8-month calibration window. Substantial fleet topology shifts would require reviewing these thresholds.
- **Pairwise comparison scope.** `RankingComparisonReport` highlights score differentials and dominant feature contrasts between any two gateways, but does not simulate counterfactual hypothetical feature interventions.

### API & Live-Demo Limitations (Phase 6)

- **Node-local in-memory cache.** Weekly predictions are cached in a thread-safe `ConcurrentHashMap`. Restarting the application clears the cache, requiring the first prediction request for a week to run the ~1.5s analytical extraction pipeline before subsequent requests return in <1ms.
- **Synchronous rerun pipeline.** The `POST /rerun` endpoint executes feature extraction and ranking synchronously. While optimized via DuckDB fleet aggregation (~1.5s total), burst concurrent reruns will queue on the single in-process DuckDB instance.
- **Rerun cache consistency under concurrent load.** The `/rerun` endpoint evicts the cache entry (`remove`) then recomputes (`computeIfAbsent`). These two operations are not atomic: under extreme concurrent load, a concurrent `GET` request arriving between eviction and recomputation could theoretically be served a stale cached result re-inserted by another thread. In the expected competition and operator-demo usage profile (single operator, sequential requests), this window is negligible.
- **Unauthenticated REST boundary.** By competition design, the REST API does not enforce JWT/OAuth authentication; production deployment requires fronting with an API gateway or reverse proxy with TLS termination.


### Out of Scope

- End-user web UI / frontend dashboard (API / CLI export oriented)
- Real-time streaming telemetry ingestion (Kafka/MQTT)
- Multi-tenant tenant isolation
- Database write-back migrations (no persistent state beyond read-only analytical stores)

