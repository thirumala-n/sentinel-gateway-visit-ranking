# Sentinel — Gateway Visit Prioritization Service

> **NEXORA 2026 Challenge** · Internal project name: *Sentinel*

Sentinel ranks gateway visits to help field engineers prioritise which gateways to inspect each week, based on telemetry anomalies, visit history, and operational context.

---

## Problem

Field engineers have a limited budget of 15 weekly visits. Sentinel analyses historical telemetry and meter data across a 28-day baseline window to produce an evidence-backed, deterministic ranking of the 15 highest-value gateway visits each week, accompanied by structured decision evidence for operators.

## Competition Track

NEXORA 2026 — Gateway Visit Ranking Service (Software Development Track).

---

## Architecture

Sentinel follows a **hexagonal (ports & adapters) architecture**:

```
┌─────────────────────────────────────────────┐
│                  web                        │
│  controllers · DTOs · exception handling    │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│              application                    │
│  prediction service · caching · csv export  │
│  explanation service · comparison engine    │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│                domain                       │
│  models · ranking strategies · features     │
│  (framework-independent, 0 Spring/IO deps)  │
└─────────────────────────────────────────────┘
                   ▲
┌──────────────────┴──────────────────────────┐
│            infrastructure                   │
│  DuckDB · CSV · Excel · configuration       │
│  (implements application output ports)      │
└─────────────────────────────────────────────┘
```

**Key design rule:** `domain` has zero dependencies on Spring, databases, or I/O libraries (enforced via ArchUnit tests).

---

## Technology Stack

| Layer         | Technology                                  |
|---------------|---------------------------------------------|
| Language      | Java 21 (LTS)                               |
| Framework     | Spring Boot 3.5.x                           |
| Build         | Maven (`./mvnw`)                            |
| Analytics     | DuckDB (in-process analytical SQL engine)   |
| Serialization | Apache Commons CSV (RFC-4180), Jackson      |
| Excel         | Apache POI OOXML                            |
| API Docs      | springdoc-openapi (Swagger UI, OpenAPI 3.0) |
| Testing       | JUnit 5, Mockito, AssertJ, ArchUnit         |
| Code Style    | Spotless (Google Java Format)               |
| Observability | Spring Boot Actuator (`/actuator/health`)   |

---

## Quick Start: Run the API

### Prerequisites
- Java 21+
- Available competition dataset in `./data` (`data/telemetry`, `data/gateway_master.csv`, etc.)

### 1. Start the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or package and run the JAR
./mvnw clean package -DskipTests
java -jar target/sentinel-0.0.1-SNAPSHOT.jar
```

The service starts on `http://localhost:8080`.

---

## REST API Endpoints & Examples

Base URL: `http://localhost:8080/api/v1`

### 1. Get Weekly Predictions (15 Ranked Decisions)
```bash
# ISO Date format
curl -s http://localhost:8080/api/v1/predictions/2026-03-09

# ISO Week format
curl -s http://localhost:8080/api/v1/predictions/2026-W11
```
**Example Response:**
```json
{
  "week_start": "2026-03-09",
  "total_selected": 15,
  "capacity": 15,
  "ranking_method": "RISK_BASED",
  "generated_at": "2026-09-15T02:32:54.616Z",
  "predictions": [
    {
      "rank": 1,
      "gateway_id": "02D3289B907C",
      "score": 84.12,
      "reason": "168h continuous outage; 168 offline anomalies; recent outage is accelerating. 156 meters affected.",
      "decision_category": "MISSING_TELEMETRY",
      "risk_level": "CRITICAL",
      "confidence": "MISSING_TELEMETRY",
      "fallback": false
    }
  ]
}
```

### 2. Explain a Gateway Ranking
Answers: *"Why is this gateway ranked where it is?"*
```bash
curl -s http://localhost:8080/api/v1/predictions/2026-03-09/02D3289B907C/explain
```
**Example Response:**
```json
{
  "gateway_id": "02D3289B907C",
  "rank": 1,
  "score": 84.12,
  "decision_category": "MISSING_TELEMETRY",
  "risk_level": "CRITICAL",
  "confidence": "MISSING_TELEMETRY",
  "fallback": false,
  "primary_reason": "168h continuous outage; 168 offline anomalies; recent outage is accelerating. 156 meters affected.",
  "summary": "Primary driver is sustained complete offline failure. Zero telemetry received across entire scoring week.",
  "feature_contributions": [
    {
      "feature_code": "F01",
      "feature_name": "Flagged Offline Hours",
      "value": "168h",
      "severity": "CRITICAL",
      "active": true
    }
  ],
  "warnings": ["No telemetry received in scoring week; communications failure cannot be excluded."],
  "limitations": ["Subject to baseline drift if outage spans > 28 days."]
}
```

### 3. Pairwise Comparison Report
Answers: *"Why is Gateway A ranked above Gateway B?"*
```bash
curl -s http://localhost:8080/api/v1/predictions/2026-03-09/02D3289B907C/compare/029E65D7B701
```
**Example Response:**
```json
{
  "higher_gateway_id": "02D3289B907C",
  "lower_gateway_id": "029E65D7B701",
  "reasons": [
    "Gateway 02D3289B907C is categorized MISSING_TELEMETRY vs Gateway 029E65D7B701 categorized ACTIONABLE_RISK.",
    "Gateway 02D3289B907C has a 168h continuous outage vs Gateway 029E65D7B701's 14h (F03: 168 > 14).",
    "Score: 02D3289B907C=84.12 vs 029E65D7B701=65.40."
  ],
  "score_proximity_warning": false
}
```

### 4. Rerun Predictions (Explicit Cache Invalidation)
Forces re-reading source data from disk and recalculating the ranking:
```bash
curl -X POST -s http://localhost:8080/api/v1/predictions/2026-03-09/rerun
```

### 5. Health & Swagger Documentation
- **Health Check**: `http://localhost:8080/actuator/health`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Spec**: `http://localhost:8080/v3/api-docs`

---

## Live-Demo Strategy Switching

The ranking strategy can be swapped on the fly via Spring configuration without code changes:

```bash
# Run with Risk-Based Strategy (Default)
java -jar target/sentinel-0.0.1-SNAPSHOT.jar

# Run with Baseline 3-Sigma Anomaly Strategy
java -Dsentinel.ranking-strategy=baseline -jar target/sentinel-0.0.1-SNAPSHOT.jar

# Or via environment variable
export SENTINEL_RANKING_STRATEGY=baseline
./mvnw spring-boot:run
```

Both strategies satisfy the identical API contract (exactly 15 decisions, unique gateways, continuous ranks 1..15).

---

## Testing & Quality Assurance

```bash
# Run full automated test suite (196 tests)
./mvnw clean test

# Verify code formatting (Google Java Format)
./mvnw spotless:check

# Auto-apply code formatting
./mvnw spotless:apply
```

### Key Test Suites:
- **`PredictionApiE2EIntegrationTest`**: Full-stack HTTP $\to$ DuckDB $\to$ Parquet $\to$ Ranking $\to$ Explanation $\to$ JSON test.
- **`PredictionControllerWebMvcTest`**: MockMvc test covering all endpoints and structured error codes (400, 404, 422).
- **`PredictionsCsvConsistencyTest`**: Proves bitwise agreement between API predictions and generated `predictions.csv`.
- **`PredictionRerunDeterminismTest`**: Verifies cache eviction, recomputation, and deterministic repeatability.
- **`RankingStrategySubstitutionTest`**: Proves seamless strategy swapping.
- **`DuckDbTelemetryRepositoryRegressionTest`**: Preserved bug-driven regression test for Hive partition globbing and timestamp bindings.
- **`ArchitectureTest`**: ArchUnit tests guaranteeing domain isolation (zero Spring/Web/Infrastructure dependencies in domain).

---

## Configuration Reference

All settings can be overridden via environment variables or JVM system properties:

| Property                         | Env Variable                   | Default      | Description                                       |
|----------------------------------|--------------------------------|--------------|---------------------------------------------------|
| `sentinel.data-dir`              | `SENTINEL_DATA_DIR`            | `data`       | Path to source telemetry and inventory data       |
| `sentinel.visits-per-week`       | `SENTINEL_VISITS_PER_WEEK`     | `15`         | Number of visit recommendations per week          |
| `sentinel.baseline-window-days`  | `SENTINEL_BASELINE_WINDOW_DAYS`| `28`         | Trailing baseline window size $[T-28d, T)$        |
| `sentinel.recent-window-days`    | `SENTINEL_RECENT_WINDOW_DAYS`  | `7`          | Scoring anomaly window size $[T-7d, T)$           |
| `sentinel.ranking-strategy`      | `SENTINEL_RANKING_STRATEGY`    | `risk-based` | Active ranking strategy (`risk-based`, `baseline`)|

---

## Data Safety Guarantee

- Challenge data in `/data` is strictly ignored in `.gitignore` and `.dockerignore`.
- DuckDB queries operate in **read-only mode** directly against Parquet files.
- `git ls-files data/` returns 0 files.
