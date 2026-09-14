# Sentinel — Gateway Visit Prioritization Service

> **NEXORA 2026 Challenge** · Internal project name: *Sentinel*

Sentinel ranks gateway visits to help field engineers prioritise which gateways to inspect each week, based on telemetry anomalies, visit history, and operational context.

---

## Problem

Field engineers have a limited number of weekly visits. Sentinel analyses historical telemetry and visit data to produce a ranked list of gateways that should be visited in each upcoming week, maximising the value of every field trip.

## Competition Track

NEXORA 2026 — Gateway Visit Ranking Service.

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
│  services · input ports · output ports      │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│                domain                       │
│  models · ranking strategies · features     │
│  (framework-independent)                    │
└─────────────────────────────────────────────┘
                   ▲
┌──────────────────┴──────────────────────────┐
│            infrastructure                   │
│  DuckDB · CSV · Excel · configuration       │
│  (implements application output ports)      │
└─────────────────────────────────────────────┘
```

**Key design rule:** `domain` has zero dependencies on Spring, databases, or I/O libraries.

## Technology Stack

| Layer         | Technology                                  |
|---------------|---------------------------------------------|
| Language      | Java 21                                     |
| Framework     | Spring Boot 3.5.x                           |
| Build         | Maven                                       |
| Data Engine   | DuckDB (in-process OLAP)                    |
| CSV           | Apache Commons CSV                          |
| Excel         | Apache POI OOXML                            |
| API Docs      | springdoc-openapi (Swagger UI)              |
| Testing       | JUnit 5, Mockito, AssertJ, ArchUnit         |
| Formatting    | Spotless                                    |
| Containers    | Docker, Docker Compose                      |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)
- Docker & Docker Compose (optional)

### Build & Run

```bash
# Build
./mvnw clean package

# Run
java -jar target/sentinel-0.0.1-SNAPSHOT.jar

# Or with Docker
cd docker && docker compose up --build
```

### Run Tests

```bash
./mvnw clean test
```

### Format Code

```bash
./mvnw spotless:apply    # auto-format
./mvnw spotless:check    # verify formatting
```

## API

> **Base URL:** `http://localhost:8080/api/v1`

| Method | Endpoint                                    | Description                |
|--------|---------------------------------------------|----------------------------|
| GET    | `/predictions/{week}`                       | Ranked gateway predictions |
| GET    | `/predictions/{week}/{gatewayId}/explain`   | Explain a gateway's score  |
| POST   | `/predictions/{week}/rerun`                 | Re-run ranking for a week  |

<!-- TODO: Document request/response schemas once endpoints are implemented. -->

## Ranking Strategy

<!-- TODO: Document the composite ranking strategy, feature engineering, and scoring methodology once implemented. -->

The ranking engine will use a composite scoring approach combining multiple features (persistence, trend, meter impact) to produce a weekly prioritised visit list.

## Testing

```bash
./mvnw clean test
```

Test categories:
- **Smoke tests** — Spring Boot context loads
- **Architecture tests** — ArchUnit layer boundary enforcement
- **Unit tests** — Domain and application logic *(TODO)*
- **Integration tests** — Infrastructure adapters *(TODO)*
- **API tests** — MockMvc controller tests *(TODO)*

## Configuration

All configuration is externalised via environment variables:

| Property                         | Env Variable                  | Default |
|----------------------------------|-------------------------------|---------|
| `sentinel.data-dir`              | `SENTINEL_DATA_DIR`           | `data`  |
| `sentinel.visits-per-week`       | `SENTINEL_VISITS_PER_WEEK`    | `15`    |
| `sentinel.baseline-window-days`  | `SENTINEL_BASELINE_WINDOW_DAYS`| `28`   |
| `sentinel.recent-window-days`    | `SENTINEL_RECENT_WINDOW_DAYS` | `7`     |
| `sentinel.scored-weeks`          | `SENTINEL_SCORED_WEEKS`       | `4`     |

## Data Safety

- The `/data` directory is listed in `.gitignore` — challenge data **never** enters version control.
- `*.parquet` files are globally ignored.
- `.dockerignore` prevents data from entering Docker build context.
- Docker Compose mounts data as **read-only** (`./data:/app/data:ro`).

## What It Cannot Do

See [LIMITATIONS.md](LIMITATIONS.md) for a full list of known constraints and out-of-scope items.

## Development

```bash
# Format
./mvnw spotless:apply

# Verify architecture rules
./mvnw test -Dtest=ArchitectureTest

# Full CI pipeline locally
./mvnw clean verify spotless:check
```

## License

This project was created for the NEXORA 2026 challenge. All rights reserved per challenge terms.
