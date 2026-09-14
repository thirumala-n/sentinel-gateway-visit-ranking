# Sentinel — Known Limitations

This document lists known limitations, constraints, and out-of-scope items.

---

## Current Limitations

### Phase 1 (Foundation)

- **No ranking logic implemented.** The project foundation exists but no scoring, feature engineering, or prediction generation has been built yet.
- **No REST endpoint logic.** API structure is prepared but endpoints return no business data.
- **No data loading.** DuckDB, CSV, and Excel adapters are not yet implemented.
- **No authentication/authorisation.** The API is unauthenticated by design (internal service for challenge evaluation).

### Inherent Constraints

- **Single-node only.** DuckDB is an in-process database; horizontal scaling is not supported.
- **Batch-oriented.** The service processes data in batch per week, not in real-time.
- **No ML/AI models.** Ranking is rule-based and heuristic, not machine-learned.
- **Read-only data.** The service does not modify challenge source data.

### Out of Scope

- User interface / frontend
- Real-time streaming telemetry
- Multi-tenant support
- Cloud deployment (Kubernetes, managed services)
- Database migrations (no persistent state beyond challenge data)

---

<!-- Additional limitations will be documented as implementation reveals them. -->
