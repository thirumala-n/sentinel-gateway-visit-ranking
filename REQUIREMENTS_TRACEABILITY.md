# Sentinel — Requirements Traceability Matrix

This document maps challenge requirements to their implementation status.

---

## Functional Requirements

| ID    | Requirement                                    | Status      | Implementation                    |
|-------|------------------------------------------------|-------------|-----------------------------------|
| FR-01 | Rank gateways for weekly visits                | 🔲 Planned  | `domain.ranking`                  |
| FR-02 | Produce predictions.csv output                 | 🔲 Planned  | `infrastructure.csv`              |
| FR-03 | Support configurable visits per week           | ✅ Done      | `SentinelProperties.visitsPerWeek`|
| FR-04 | REST API for predictions                       | 🔲 Planned  | `web.controller`                  |
| FR-05 | REST API for explainability                    | 🔲 Planned  | `web.controller`                  |
| FR-06 | REST API for re-run                            | 🔲 Planned  | `web.controller`                  |
| FR-07 | Use historical telemetry for baseline          | 🔲 Planned  | `domain.feature`                  |
| FR-08 | Detect anomalies vs baseline                   | 🔲 Planned  | `domain.feature`                  |

## Non-Functional Requirements

| ID    | Requirement                                    | Status      | Implementation                    |
|-------|------------------------------------------------|-------------|-----------------------------------|
| NF-01 | Java 21                                        | ✅ Done      | `pom.xml`                         |
| NF-02 | Spring Boot 3.5.x                              | ✅ Done      | `pom.xml`                         |
| NF-03 | Maven build                                    | ✅ Done      | `pom.xml`, `mvnw`                 |
| NF-04 | Docker support                                 | ✅ Done      | `docker/`                         |
| NF-05 | Challenge data never in Git                    | ✅ Done      | `.gitignore`                      |
| NF-06 | Clean architecture                             | ✅ Done      | Package structure, ArchUnit       |
| NF-07 | API documentation (OpenAPI)                    | ✅ Done      | `springdoc-openapi`               |
| NF-08 | Code formatting (Spotless)                     | ✅ Done      | `pom.xml` Spotless plugin         |
| NF-09 | CI pipeline                                    | ✅ Done      | `.github/workflows/ci.yml`        |
| NF-10 | Automated tests                                | 🔶 Partial  | Smoke + ArchUnit; more planned    |

## Documentation Requirements

| ID    | Requirement                                    | Status      | File                              |
|-------|------------------------------------------------|-------------|-----------------------------------|
| DR-01 | README                                         | ✅ Done      | `README.md`                       |
| DR-02 | Architecture decisions                         | ✅ Done      | `DECISIONS.md`                    |
| DR-03 | Known limitations                              | ✅ Done      | `LIMITATIONS.md`                  |
| DR-04 | AI usage disclosure                            | ✅ Done      | `AI-USAGE.md`                     |
| DR-05 | Requirements traceability                      | ✅ Done      | `REQUIREMENTS_TRACEABILITY.md`    |
| DR-06 | API examples                                   | ✅ Done      | `docs/api-examples.http`          |

---

<!-- This matrix will be updated as implementation phases are completed. -->
