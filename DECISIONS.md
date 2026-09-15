# Sentinel — Architecture & Design Decisions

This document records key technical decisions made during development.

---

## ADR-001: Hexagonal Architecture

**Status:** Accepted

**Context:** The challenge requires a maintainable, testable service with clear separation between business logic and infrastructure concerns.

**Decision:** Adopt hexagonal (ports & adapters) architecture with three primary layers:
- `domain` — pure business logic, no framework dependencies
- `application` — orchestration via services and port interfaces
- `web` — HTTP controllers and DTOs
- `infrastructure` — implements output ports (DuckDB, CSV, Excel)

**Consequences:** Domain logic is independently testable. Infrastructure can be swapped without touching business rules. ArchUnit enforces boundaries at build time.

---

## ADR-002: DuckDB as Analytical Engine

**Status:** Accepted

**Context:** The challenge data includes Parquet files and requires analytical queries. A full RDBMS (PostgreSQL, MySQL) would be unnecessary overhead.

**Decision:** Use DuckDB in-process OLAP database via JDBC. No external database server required.

**Consequences:** Zero infrastructure setup. Parquet files can be queried directly. The entire application runs as a single JAR.

---

## ADR-003: Data Never Enters Version Control

**Status:** Accepted

**Context:** Challenge data is confidential and must not be leaked.

**Decision:** `/data` directory and `*.parquet` files are in `.gitignore`. Docker mounts data as read-only volumes. `.dockerignore` excludes data from build context.

**Consequences:** Challenge data integrity is maintained. Builds require data to be supplied externally.

---

## ADR-004: RankingStrategy Operates on Immutable Data

**Status:** Accepted

**Context:** Initial design considered having RankingContext hold repository references directly.

**Decision:** Repositories load data → Application service builds immutable RankingContext → RankingStrategy operates purely on data, not on repositories.

**Consequences:** Strategies are pure functions on data, easily testable without mocking repositories.

---

## ADR-005: Spring Boot 3.5.x with Java 21

**Status:** Accepted

**Context:** Need a modern, stable framework with good ecosystem support.

**Decision:** Use Spring Boot 3.5.16 on Java 21.

**Consequences:** Access to virtual threads, records, pattern matching. Mature Spring ecosystem for web, validation, and actuator.

---

## ADR-013: Explanation Architecture — Domain-Only, Deterministic

**Status:** Accepted

**Context:** Phase 5 requires every ranked visit decision to be explainable. Options considered:
1. LLM-generated natural language
2. Template-based text with stored R_tech
3. Deterministic structural domain model, recomputing R_tech from feature values

**Decision:** Implement a pure domain + application layer explanation model with no LLM, no external dependencies, and no stored intermediate values. R_tech is recomputed deterministically from feature values using the same formula as `RiskBasedRankingStrategy`, not reverse-engineered from the final score.

**Key design details:**
- `DecisionCategory` derived from `DataConfidence` + `fallback` flag + `R_tech` threshold (priority order: `MISSING_TELEMETRY` > `FALLBACK` > `LOW_CONFIDENCE` > `ACTIONABLE_RISK` > `HIGH_PRIORITY`)
- `RiskLevel` derived from F03 consecutive outage, F01 offline anomaly volume, and `R_tech`
- `FeatureContribution` severity labels anchored to normalization denominators in ranking formula (F01=84h, F02=24h, F03=48h)
- `csvReason` capped at 300 characters, assembled only from active signals
- `RankingComparisonReport` generates pairwise "why A > B" with score proximity warning (threshold: 5.0)

**Consequences:**
- Explanations are fully traceable to features and documented thresholds
- Same inputs always produce identical outputs (provable via determinism tests)
- No hallucination risk — every claim references an actual measured feature value
- ArchUnit enforces domain layer isolation — no Spring/infrastructure dependencies in explanation classes

## ADR-014: REST API Architecture, In-Memory Caching & Strategy Substitution

**Status:** Accepted

**Context:** Phase 6 requires exposing predictions, explanations, comparisons, and reruns over HTTP. Controllers must remain thin, responses must be fast for live demos (<1ms), strategy substitution must be seamless without code changes, and API predictions must 100% match `predictions.csv`.

**Decision:**
1. **Hexagonal Isolation:** `PredictionController` depends exclusively on `GatewayPredictionApplicationService` and DTOs; controllers contain zero scoring, feature calculation, or data access logic.
2. **Canonical Decision Model:** `WeeklyPredictionsResult` is the single source of truth for both REST API responses (`WeeklyPredictionsResponse`) and RFC-4180 CSV serialization (`PredictionsCsvExporter`). Both share identical objects, guaranteeing zero divergence.
3. **In-Memory Caching with Explicit Invalidation:** `GatewayPredictionApplicationService` caches weekly predictions in a thread-safe `ConcurrentHashMap`. Repeated calls to predictions, explanations, or comparisons are served in <1ms without re-querying Parquet. The `POST /api/v1/predictions/{week}/rerun` endpoint explicitly evicts the cache and recalculates from source data.
4. **Live-Demo Strategy Substitution:** `RankingStrategy` is bound via Spring configuration (`sentinel.ranking-strategy`). Setting `sentinel.ranking-strategy=baseline` swaps `RiskBasedRankingStrategy` for `BaselineRankingStrategy` with zero application service or controller changes.
5. **Strict Temporal & Format Validation:** `PredictionWeekParser` supports both ISO date (`YYYY-MM-DD`) and ISO week (`YYYY-Www`) formats, enforcing Monday alignment, rejecting future weeks beyond telemetry (>2026-03-30) with 422 `FUTURE_WEEK`, and rejecting pre-baseline weeks (<2025-09-01) with 422 `UNSUPPORTED_WEEK`.

## ADR-015: Operator Decision Console as Single-File Static Consumer

**Status:** Accepted

**Context:** Phase 7 requires an intuitive, competition-grade operator decision interface to inspect weekly visit priorities, drill into decision evidence, contrast pairwise rankings, trigger cache recalculation, and export decisions. Evaluators must be able to comprehend the system's reasoning within seconds without managing separate frontend build pipelines, Node.js runtimes, or external network dependencies.

**Decision:**
1. **Single-File Vanilla Web Architecture:** The dashboard is implemented as a single, self-contained HTML/CSS/JavaScript file (`src/main/resources/static/index.html`) served directly by Spring Boot at `http://localhost:8080/`.
2. **Zero External Runtime Dependencies:** No external JS frameworks (React, Vue, Angular), no build toolchains (Vite, Webpack, npm), no CDN fonts, and no external analytics. Uses the native system font stack (`-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`) to ensure 100% offline functionality.
3. **Backend as Single Source of Truth:** The console is strictly a read-only consumer of the REST API (`fetch()`). The frontend never calculates risk scores, feature values, or ranking thresholds; all displayed evidence and metrics originate directly from `/api/v1/predictions/{week}`, `/explain`, `/compare`, and `/actuator/health`.
4. **Live-Session Resilience:** Provides visual feedback for system health, in-flight recalculations (spinner on rerun), non-blocking error alerts with actionable codes, and client-side RFC-4180 CSV export of currently loaded weekly predictions.

**Consequences:**
- Zero deployment overhead: starting the Spring Boot JAR automatically serves the decision console.
- Zero drift risk between dashboard numbers and backend ranking logic.
- Evaluators can review the full decision lifecycle in under 90 seconds.


