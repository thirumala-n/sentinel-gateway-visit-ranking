# Sentinel — AI Usage Disclosure

This document records how AI tools were used during the design, development, and hardening of the Sentinel project for the NEXORA 2026 challenge.

---

## AI Tools Used

| Tool | Purpose / Scope |
| :--- | :--- |
| **Antigravity AI (Google DeepMind)** | Pair programming, test generation, refactoring, architectural auditing, Spotless formatting |

---

## Usage Details Across Development Phases

### Phase 1 — Foundation & Hexagonal Architecture
- Generated initial Maven POM configuration, Java 21 toolchains, and package structure.
- Scaffolded ArchUnit architecture boundary enforcement rules (`ArchitectureTest.java`).
- Configured DuckDB in-process JDBC data source and Spring Boot 3.5 application properties.

### Phase 2 — Data Quality & Baseline Reproduction
- Assisted in drafting initial exploration scripts (`scratch/DataAnalysis.java`).
- Generated statistical anomaly calculation logic reproducing the reference 3-sigma algorithm.
- Identified duplicate gateway-hours (0.91%) and collinearity between disconnects and offline seconds ($r = 0.94$).

### Phase 3 — Feature Engineering Engine
- Implemented individual feature computers (`F01FlaggedOfflineHoursComputer`, `F02FlaggedRebootHoursComputer`, `F03MaxConsecutiveOfflineComputer`, `F04TrendTrajectoryComputer`, `F06CumulativeExcessZComputer`).
- Constructed comprehensive parameterized unit tests covering window edge conditions and zero-variance stability.

### Phase 4 — Ranking Strategy & Historical Backtesting
- Drafted `RankingStrategy` domain interface and `RiskBasedRankingStrategy` implementation.
- Implemented the 5-tier deterministic comparator (`score` $\to$ `F03` $\to$ `F01` $\to$ `F05` $\to$ `gatewayId`).
- Scaffolding of historical backtest runner across 11 historical validation weeks.

### Phase 5 — Explainability & Pairwise Contrast
- Generated `DecisionExplanationService`, `RankingComparisonReport`, and severity label mapping.
- Structured RFC-4180 CSV reason generation ensuring strict $\le 300$ character hard boundaries.
- Authored adversarial determinism and property-based test suites.

### Phase 6 — Production REST API & Caching
- Scaffolded Spring MVC controllers (`PredictionController`), DTO records, and RFC-7807 `GlobalExceptionHandler`.
- Implemented thread-safe `ConcurrentHashMap` caching with explicit cache eviction on `/rerun`.
- Implemented full-stack mock and real MockMvc test suites covering 200/400/404/422 status codes.

### Phase 7 & 7B — Operator Decision Console
- Generated single-file, zero-dependency HTML5/CSS/JavaScript console (`src/main/resources/static/index.html`).
- Implemented dual-theme (enterprise light / deep dark) design system, live rerun polling, and modal drilldowns.
- Ensured 100% offline capability with zero external CDN dependencies.

### Phase 8 — Submission Hardening & Gate Verification
- Automated multi-week batch export in `PredictionsCsvExporter`.
- Authored `PredictionsCsvGenerationTest` (120 rows, ranks 1..15, zero duplicates, $\le 300$ char reasons).
- Authored `RerunDynamicDataIntegrationTest` proving zero-restart dynamic parquet partition ingestion.

---

## Concrete AI Mistakes Caught & Corrected by Human Oversight

During development, AI tooling made several concrete errors that required human diagnosis, engineering intervention, and code correction:

1. **Prediction Weeks Horizon Offset:**
   - *AI Error:* AI initially generated code and test assertions targeting prediction weeks `2026-02-09` through `2026-03-30` (shifting the window by 1 week based on an unverified audit report claim).
   - *Human Correction:* Verified against the official authoritative NEXORA Round One FAQ and Round Two FAQ, which strictly specify the 8 prediction weeks are: `2026-02-02`, `2026-02-09`, `2026-02-16`, `2026-02-23`, `2026-03-02`, `2026-03-09`, `2026-03-16`, and `2026-03-23`. Updated `PredictionWeek.OFFICIAL_COMPETITION_WEEKS`, regenerated `predictions.csv`, and updated all test harnesses.

2. **DuckDB Multi-Partition Parquet Type Incompatibility:**
   - *AI Error:* In generating synthetic parquet files for dynamic partition tests (`RerunDynamicDataIntegrationTest`), AI emitted raw numeric values that DuckDB inferred as `DECIMAL(2,1)` in one partition and `DECIMAL(5,1)` in another, causing DuckDB's multi-file scanner to crash with `Conversion Error: Unimplemented type for cast (DECIMAL(2,1) -> DECIMAL(5,1))`.
   - *Human Correction:* Diagnosed the DuckDB schema inference issue across multi-partition globs and enforced explicit `DOUBLE` casts during parquet generation.

3. **DuckDB Hive Globbing Resolution on Subdirectories:**
   - *AI Error:* AI wrote DuckDB query paths using double asterisks (`read_parquet('data/telemetry/**/*.parquet')`), which failed to bind Hive partition values properly on nested directories under Windows.
   - *Human Correction:* Corrected to explicit Hive partition discovery syntax: `read_parquet('data/telemetry/*/*.parquet', hive_partitioning=1)`.

---

## Human Engineering Oversight & Authorship

All AI outputs were subjected to rigorous human staff-level engineering oversight:
1. **Core Algorithm Ownership:** The mathematical formulation of the staged ranking strategy ($R_{\text{tech}}$ additive core, logarithmic impact multiplier $M_{\text{impact}}$, $\sigma_{\min}$ variance floors) was directed and validated by human judgement based on field economics.
2. **Economic Tradeoffs:** The distinction between official competition scoring (€380 / €600 per gateway) and internal operational meter weighting was formulated by human review.
3. **Temporal Anti-Leakage:** Human adversarial audits verified that zero observations timestamped $\ge T_{\text{target}}$ could be accessed during scoring.
4. **Code Quality:** All 200 automated tests, ArchUnit rules, Spotless formatting, and RFC-7807 error responses were executed and verified against real dataset files.
