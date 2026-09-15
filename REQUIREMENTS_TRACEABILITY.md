# Sentinel — Requirements Traceability Matrix

This document maps challenge requirements to their implementation status.

---

## Functional Requirements

| ID    | Requirement                                    | Status      | Implementation                    |
|-------|------------------------------------------------|-------------|-----------------------------------|
| FR-01 | Rank gateways for weekly visits                | ✅ Done      | `domain.ranking.RiskBasedRankingStrategy` |
| FR-02 | Produce predictions.csv output                 | ✅ Done      | `infrastructure.csv.PredictionsCsvExporter` |
| FR-03 | Support configurable visits per week           | ✅ Done      | `SentinelProperties.visitsPerWeek` |
| FR-04 | REST API for predictions                       | ✅ Done      | `web.controller.PredictionController.getPredictions` |
| FR-05 | REST API for explainability                    | ✅ Done      | `web.controller.PredictionController.getExplanation` & `compare` |
| FR-06 | REST API for re-run                            | ✅ Done      | `web.controller.PredictionController.rerun` |
| FR-07 | Use historical telemetry for baseline          | ✅ Done      | `DuckDbTelemetryRepository` (28-day baseline window) |
| FR-08 | Detect anomalies vs baseline                   | ✅ Done      | `domain.feature` (F01–F06 computers) |

## Non-Functional Requirements

| ID    | Requirement                                    | Status      | Implementation                    |
|-------|------------------------------------------------|-------------|-----------------------------------|
| NF-01 | Java 21                                        | ✅ Done      | `pom.xml`                         |
| NF-02 | Spring Boot 3.5.x                              | ✅ Done      | `pom.xml`                         |
| NF-03 | Maven build                                    | ✅ Done      | `pom.xml`, `mvnw`                 |
| NF-04 | Docker support                                 | ✅ Done      | `docker/`, `compose.yaml`         |
| NF-05 | Challenge data never in Git                    | ✅ Done      | `.gitignore`                      |
| NF-06 | Clean architecture                             | ✅ Done      | Package structure, ArchUnit       |
| NF-07 | API documentation (OpenAPI)                    | ✅ Done      | `springdoc-openapi`               |
| NF-08 | Code formatting (Spotless)                     | ✅ Done      | `pom.xml` Spotless plugin         |
| NF-09 | CI pipeline                                    | ✅ Done      | `.github/workflows/ci.yml`        |
| NF-10 | Automated tests                                | ✅ Done      | 200 automated tests (Unit, E2E, ArchUnit, Rerun) |

## Documentation Requirements

| ID    | Requirement                                    | Status      | File                              |
|-------|------------------------------------------------|-------------|-----------------------------------|
| DR-01 | README                                         | ✅ Done      | `README.md`                       |
| DR-02 | Architecture decisions                         | ✅ Done      | `DECISIONS.md`                    |
| DR-03 | Known limitations                              | ✅ Done      | `LIMITATIONS.md`                  |
| DR-04 | AI usage disclosure                            | ✅ Done      | `AI-USAGE.md`                     |
| DR-05 | Requirements traceability                      | ✅ Done      | `REQUIREMENTS_TRACEABILITY.md`    |
| DR-06 | API examples                                   | ✅ Done      | `docs/api-examples.http`          |
| DR-07 | Historical backtesting                         | ✅ Done      | `BACKTESTING.md`                  |

---

<!-- This matrix will be updated as implementation phases are completed. -->
