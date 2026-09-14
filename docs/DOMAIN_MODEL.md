# Sentinel Domain Model — Phase 3 Specification

## 1. Architectural Overview & Boundaries

Sentinel follows Clean / Hexagonal Architecture (Ports and Adapters). The Domain Model encapsulates all business invariants, entities, value objects, and repository port definitions. It is strictly decoupled from framework details:

- **Zero Spring Framework dependencies** in `com.lpdg.sentinel.domain.*` (enforced via ArchUnit).
- **Zero persistence/database dependencies** (DuckDB, Parquet, Jackson, and CSV libraries reside exclusively in `com.lpdg.sentinel.infrastructure.*`).
- **Zero Web / REST dependencies** (Controllers and HTTP serialization reside exclusively in `com.lpdg.sentinel.web.*`).

```
┌────────────────────────────────────────────────────────────────────────┐
│                        INFRASTRUCTURE LAYER                            │
│  DuckDbGatewayRepository  DuckDbTelemetryRepository  CsvMeterReadRepo  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ implements ports
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         APPLICATION LAYER                              │
│    FeatureExtractionService ──► [F01-F06 Feature Computers]           │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ coordinates domain
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           DOMAIN LAYER                                 │
│  Model:     GatewayId, PredictionWeek, Gateway, BaselineStats          │
│  Features:  GatewayFeatures, DataConfidence                            │
│  Ranking:   RankingContext, RankingStrategy (Port)                     │
│  Ports:     GatewayRepository, TelemetryRepository, MeterReadRepo      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Domain Types & Invariants

### 2.1 `GatewayId` (Value Object)
- **Purpose**: Canonical representation of a gateway hardware identifier.
- **Invariants**:
  - Exactly 12 hexadecimal characters (0-9, A-F), uppercase.
  - Strips colons (`:`) and spaces during normalization (resolves DQ-01).
  - Null or blank values throw `IllegalArgumentException`.
  - Values not conforming to 12 hex characters throw `IllegalArgumentException`.
- **Factory**: `GatewayId.of(String raw)` or `GatewayId.normalize(String raw)`.

### 2.2 `PredictionWeek` (Value Object)
- **Purpose**: Encapsulates the evaluation epoch and deterministic temporal windows.
- **Invariants**:
  - `targetMonday` must be a Monday (`java.time.DayOfWeek.MONDAY`). Non-Mondays throw `IllegalArgumentException`.
  - Cannot be null.
- **Temporal Windows (Anti-Leakage Contract)**:
  - `baselineStart()`: `targetMonday - 28 days` (00:00:00 UTC, inclusive).
  - `baselineEnd()`: `targetMonday` (00:00:00 UTC, exclusive).
  - `scoringStart()`: `targetMonday - 7 days` (00:00:00 UTC, inclusive).
  - `scoringEnd()`: `targetMonday` (00:00:00 UTC, exclusive).
  - `isHistorical(Instant ts)`: strictly `ts < targetMonday.atStartOfDay(ZoneOffset.UTC).toInstant()`. Any record at or after the evaluation boundary is definitively rejected.

### 2.3 `Gateway` (Entity)
- **Purpose**: Represents physical asset metadata from gateway master inventory.
- **Fields**: `GatewayId id`, `int nMetersInstalled`, `LocalDate installedOn`, `LocalDate decommissionedOn`.
- **Invariants & Rules**:
  - `nMetersInstalled`: clamped to `>= 0`. If negative or missing, clamped/defaulted to fleet median (156).
  - `isEligible(LocalDate evalDate)`:
    - Must NOT be decommissioned prior to evaluation date: `decommissionedOn == null || !decommissionedOn.isBefore(evalDate)` (DQ-04).
    - Must NOT be a future installation: `installedOn == null || !installedOn.isAfter(evalDate)` (DQ-05).

### 2.4 `BaselineStats` (Value Object)
- **Purpose**: Summary statistics over the 28-day baseline window per gateway per metric.
- **Fields**:
  - `double mean`: historical arithmetic average.
  - `double stdDev`: sample standard deviation ($\sigma$).
  - `int observationCount`: count of deduplicated hourly samples ($N$).
  - `boolean coldStart`: `observationCount < 336` (fewer than 14 days of hourly telemetry).
- **Sigma Floor Invariant (DQ-08)**:
  - $\sigma_{\text{eff}} = \max(\sigma, \sigma_{\min})$
  - Offline duration floor: $\sigma_{\min} = 60.0\text{ s}$
  - Reboot count floor: $\sigma_{\min} = 0.5$
  - Anomaly Threshold: $\mu + 3.0 \times \sigma_{\text{eff}}$
  - Guarantees zero division errors, eliminates infinite z-scores, and prevents false-alarm triggers on zero-variance gateways.

### 2.5 `GatewayFeatures` (Entity)
- **Purpose**: Clean, standardized feature representation computed for each eligible gateway for a given prediction week.
- **Fields**:
  - `GatewayId gatewayId`
  - `PredictionWeek week`
  - `int f01FlaggedOfflineHours`: Hours where `offline_duration_sec > (mu + 3 * sigmaEff)`.
  - `int f02FlaggedRebootHours`: Hours where `reboot_cnt > (mu + 3 * sigmaEff)`.
  - `int f03MaxConsecutiveOfflineHours`: Longest uninterrupted continuous run of hours where `offline_duration_sec >= 3000s`. Must have exact 1-hour interval adjacency ($\Delta t = 3600\text{ s}$).
  - `double f04TrendTrajectoryRatio`: Ratio of offline volume in last 72h vs normalized preceding 96h, smoothed with Laplace constant (+3600s). Clamped to $[0.0, 5.0]$.
  - `int f05MetersExposure`: Installed meter density clamped to physical fleet bounds.
  - `double f06CumulativeExcessZ`: Sum of positive hourly z-score excesses $(\frac{X - \mu}{\sigma_{\text{eff}}} - 3.0)$, with each hourly increment capped at $10.0$.
  - `double f07HistoricReadSuccess`: Historical 26-week read success fraction $[0.0, 1.0]$.
  - `int f08DisconnectionFrequency`: Transition frequency between online and offline states.
  - `DataConfidence dataConfidence`: Data availability tier (`FULL`, `LOW_HISTORY`, `MISSING_TELEMETRY`).

### 2.6 `DataConfidence` (Domain Enum)
- `FULL`: Complete telemetry history ($\ge 336\text{ h}$ baseline data, $\ge 144\text{ h}$ scoring window data).
- `LOW_HISTORY`: Incomplete telemetry ($< 336\text{ h}$ baseline data). Feature baseline falls back to fleet conservative estimates.
- `MISSING_TELEMETRY`: Hard dead gateway ($0\text{ h}$ scoring telemetry). Features initialized to maximal risk values (`f01 = 168`, `f03 = 168`, `f04 = 5.0`).

### 2.7 `RankingContext` & `RankingStrategy` (Ranking Aggregate & Port)
- **`RankingContext`**: Encapsulates `PredictionWeek`, `List<GatewayFeatures>`, and metadata. It is completely decoupled from database access, holding only immutable in-memory data for ranking decisions.
- **`RankingStrategy` (Port)**:
  ```java
  List<GatewayId> rank(RankingContext context, int topN);
  ```
  Enables interchangeable ranking algorithms (Baseline, Composite, Epsilon-Greedy, Multi-Objective) in Phase 4 without modifying domain models or feature engines.

---

## 3. Data Flow: Repository to RankingContext

```
gateway_master.csv ──► GatewayRepository.findEligible(targetMonday) ──► List<Gateway>
                                                                            │
parquet telemetry  ──► TelemetryRepository.fetchScoringWindow(...)  ───────┤
                   ──► TelemetryRepository.fetchBaselineWindow(...) ───────┤
                                                                            │
meter_read_success ──► MeterReadRepository.getHistoricSuccessRate(...) ────┤
                                                                            ▼
                                                                FeatureExtractionService
                                                                ├── BaselineStatsComputer
                                                                ├── F01-F06 FeatureComputers
                                                                ▼
                                                            List<GatewayFeatures>
                                                                │
                                                                ▼
                                                          RankingContext
                                                                │
                                                                ▼
                                                        RankingStrategy.rank(context, N)
                                                                │
                                                                ▼
                                                        List<GatewayId>
```
