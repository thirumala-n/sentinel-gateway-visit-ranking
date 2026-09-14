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

<!-- Additional decisions will be added as implementation progresses. -->
