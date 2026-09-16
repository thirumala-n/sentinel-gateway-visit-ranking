# SENTINEL — Gateway Reliability & Field Planning

> **NEXORA 2026 Innovation Challenge** · Selected Track: **Software Development (60%)**  
> *Deterministic, cost-sensitive weekly field visit decision system for smart utility grids.*

---

## Quick Start (Run in Under 60 Seconds)

Sentinel requires **zero infrastructure setup**, **zero API keys**, **zero cloud dependencies**, and **zero external databases**. It runs 100% offline using in-process DuckDB OLAP.

### 1. External Data Contract (Official Challenge Data)
Per official NEXORA competition rules:
- The official challenge dataset **MUST NOT be committed** or shared in the repository (verified via `git ls-files data/` returning 0 files).
- The evaluator clones the repository and places the official challenge data into the top-level `./data` directory:
  ```
  data/
  ├── gateway_master.csv
  ├── meter_read_success.csv
  ├── field_visits.csv
  ├── engineer_review_2026-02.xlsx
  └── telemetry/
      ├── month=2025-08/*.parquet
      └── ...
  ```
- **If `./data` is missing:** The application fails fast with a clear, actionable startup or invocation exception (`NoSuchFileException` / `IllegalStateException`) stating that `./data` does not exist. It never silently falls back to hardcoded machine paths.

### 2. Start the Application (One Command)

**Windows (PowerShell / Command Prompt):**
```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
./mvnw spring-boot:run
```

*Or build and run the packaged JAR directly:*
```bash
./mvnw clean package -DskipTests
java -jar target/sentinel-0.0.1-SNAPSHOT.jar
```

### 3. Verify System Health & Call the API
```bash
# Health & Readiness Check
curl http://localhost:8080/actuator/health

# Retrieve this week's 15 prioritized visits (ISO date format)
curl http://localhost:8080/api/v1/predictions/2026-03-09

# Explain why a specific gateway was ranked where it is
curl http://localhost:8080/api/v1/predictions/2026-03-09/02D3289B907C/explain

# Pairwise comparison: why was Gateway A ranked ahead of Gateway B?
curl http://localhost:8080/api/v1/predictions/2026-03-09/02D3289B907C/compare/029E65D7B701

# Re-run ranking from source data without restarting the application
curl -X POST http://localhost:8080/api/v1/predictions/2026-03-09/rerun
```

### 4. Open the Operator Decision Console
Open your browser to:
```
http://localhost:8080/
```
The console is a self-contained, 100% offline enterprise interface served directly by Spring Boot (no Node.js, no npm, zero CDN dependencies).

---

## What It Does

Sentinel solves the **weekly technician allocation bottleneck** for municipal smart grids:
1. **Analyses 28 Days of Historical Telemetry**: Evaluates rolling baseline statistics, outage persistence, reboot clustering, and outage trajectory across the entire gateway fleet.
2. **Selects Exactly 15 Gateways Each Week**: Emits a deterministic, priority-ordered list of the 15 gateways where a physical visit resolves active hardware faults and mitigates the highest customer risk.
3. **Explains Every Decision**: Provides transparent, feature-traceable rationales linking each rank to observable measurements (e.g. `168h continuous outage. 200 meters affected`).
4. **Produces `predictions.csv`**: Generates the official competition submission artifact with exactly 120 rows (15 gateways × 8 required weeks), ranks 1–15, numeric scores, reasons $\le 300$ characters, and the canonical header `week_start,rank,gateway_id,score,reason`.
   - **Official Scored Weeks (8 Weeks):**
     `2026-02-02`, `2026-02-09`, `2026-02-16`, `2026-02-23`, `2026-03-02`, `2026-03-09`, `2026-03-16`, `2026-03-23`.
5. **Adapts to New Data Live (Round Two)**: Ingests newly dropped telemetry partitions on `POST /rerun` without requiring an application restart.

---

## Technology Stack

Sentinel is deliberately engineered for **zero infrastructure footprint**, **high-throughput analytics**, and **total determinism**. It eliminates external database and runtime dependencies by combining an embedded columnar OLAP engine with modern Java and Spring Boot.

| Layer | Technologies & Tools | Architectural Purpose |
| :--- | :--- | :--- |
| **Backend & Core** | **Java 21**, **Spring Boot 3.5.16**, Spring Web MVC, Jakarta Validation, Jackson, Spring Boot Actuator | Modern LTS Java runtime with typed domain logic, REST API routing, declarative parameter validation, and health instrumentation (`/actuator/health`). |
| **Data & Analytics** | **DuckDB 1.5.5.1** (embedded in-process), **Apache Parquet**, **Apache Commons CSV 1.14.1**, **Apache POI 5.5.1** | Vectorized SQL queries executed directly over partitioned disk Parquet files without external DBMS; tabular export for `predictions.csv`; Excel ingestion for historical engineer reviews. |
| **Architecture & Patterns** | **Ports & Adapters (Hexagonal Architecture)**, Strategy Pattern (`RankingStrategy`), Immutable Domain Models (`RankingContext`, `GatewayPrediction`) | Strict layer decoupling enforced by ArchUnit; hot-swappable ranking engines without API layer changes; deterministic tie-breaking comparator. |
| **Frontend Console** | **HTML5**, **CSS3**, **Vanilla JavaScript** (ES6+), Spring Boot Static Resource Serving | Zero-build operator dashboard (`src/main/resources/static/index.html`) served directly by Spring Boot; 100% offline with **zero Node.js/npm dependencies** and **zero external CDN links**. |
| **Testing & Quality** | **JUnit 5**, **AssertJ**, **Mockito**, **Spring Boot Test / MockMvc**, **ArchUnit 1.5.0**, **Spotless 3.10.2** | 213 automated test executions (193 distinct test definitions across 28 test classes, including 5 ArchUnit rules and repeated determinism runs). |
| **DevOps & Packaging** | **Maven Wrapper (`mvnw`)**, **Docker / Dockerfile**, **Docker Compose (`compose.yaml`)**, **GitHub Actions (`ci.yml`)** | Single-command reproducible build and execution; multi-stage non-root container deployment; automated CI build verification. |

### Deliberate Architectural Simplicity
- **Embedded Analytics Over External Databases:** DuckDB runs in-process inside the JVM, reading partitioned Apache Parquet telemetry directly from disk with zero external PostgreSQL, MySQL, or Redis processes.
- **Zero-Build Operator Dashboard:** The frontend console is implemented in pure vanilla HTML/CSS/JavaScript and served directly as a static resource by Spring Boot, eliminating Node.js, npm, webpack/vite pipelines, and CDN failure modes.
- **Pluggable Ranking Strategy:** All priority calculations are isolated behind the `RankingStrategy` interface, allowing new ranking formulations to be evaluated or substituted without modifying controllers, DTOs, or the web contract.

---

## Visualizations

Sentinel’s operator decision console (`http://localhost:8080/`) provides decision-focused visual interfaces designed around the field planning workflow:

$$\text{DATA} \longrightarrow \text{SIGNALS} \longrightarrow \text{RISK} \longrightarrow \text{PRIORITY} \longrightarrow \text{FIELD DECISION} \longrightarrow \text{EVIDENCE}$$

Rather than decorative charts or generic dashboard plots, Sentinel uses high-density operational visualizers, semantic risk badges, and comparative diff modals to give technicians and grid dispatchers immediate actionable clarity:

| Visual Interface | Visualization Type | Backing API & Data Source | Operational Purpose |
| :--- | :--- | :--- | :--- |
| **Priority #1 Command Card** | Hero visual callout card with severity indicators & signal chips | `GET /api/v1/predictions/{week}` (Rank 1 item) | Delivers instant 5-second visibility into the week's single highest-urgency gateway failure (active outage duration, meter blast radius, reboot clustering). |
| **Top-15 Work Queue** | Ranked priority table with semantic badges & score indicators | `GET /api/v1/predictions/{week}` | Displays the full 15-gateway weekly dispatch queue with rank badges (1–15), numeric composite scores, risk severity badges (`CRITICAL`, `HIGH`, `MODERATE`, `LOW`), and category tags. |
| **Evidence Drill-Down** | Modal feature breakdown with severity chips & status cards | `GET /api/v1/predictions/{week}/{id}/explain` | Decomposes individual feature contributions ($F_{01}–F_{06}$), active risk drivers, raw measurements, and operational limitations/warnings for any selected gateway. |
| **Pairwise Comparator** | Side-by-side contrast modal with delta metrics & proximity alerts | `GET /api/v1/predictions/{week}/{idA}/compare/{idB}` | Visualizes score differentials, ranking divergence, and feature-by-feature reasons justifying why Gateway A is prioritized over Gateway B (including marginal proximity warnings if $\Delta \le 0.02$). |
| **System & Health Status** | Status badge & interactive competition week selector | `GET /actuator/health` & dynamic calendar anchors | Shows live system operational health (`UP`), telemetry partition availability, and allows rapid switching across all 8 official competition Mondays. |
| **Dual Control Themes** | Light Enterprise Operations & Dark Control Room modes | Client-side CSS custom properties with persistent state | High-contrast neutral palette (Slate/Zinc) with standardized operational semantic colors (Emerald, Amber, Rose, Cyan) tailored for daylight and control-room monitor walls. |

### Architectural Integrity & Visualization Principles
- **Backend-Authoritative Rendering:** Every score, rank, risk level, decision category, and feature contribution is computed by the backend ranking engine (`DefaultRankingEngine`). The frontend performs zero independent ranking calculations and contains no mock or hardcoded datasets.
- **Purpose-Built Decision Interfaces Over Decorative Plots:** The console relies entirely on lightweight semantic HTML5/CSS3 visual components served statically by Spring Boot, eliminating third-party charting libraries (e.g., Chart.js, Recharts, D3) and ensuring 100% offline operational reliability.

---

## Screen Recording & Resume Placeholders

> **Submission Video Link:**  
> `[RECORDING PLACEHOLDER: https://youtu.be/EXAMPLE_NEXORA_SENTINEL_2026]`  
> *(Manual submission action: Record a 6–8 minute screencast demonstrating clean startup, API execution, dynamic partition rerun without restart, explainability, and the operator decision console).*
>
> **Resume Attachment:**  
> Place your `<Registration_Id>.pdf` at the repository root before submitting.

---

## NEXORA Problem & Economics

In municipal utility operations (LPDG × RGM), field technicians are a scarce, high-cost resource capped at **15 site visits per week**.

### Economic Parameters
- **Unnecessary Visit Cost:** **€380** per wasted truck roll (dispatched to a gateway where `Kein Fehler gefunden` is determined).
- **Unvisited Broken Gateway Penalty:** **€600 per week** left unvisited.
- **Important:** The official €600 penalty applies uniformly per faulty gateway and is **NOT multiplied by installed meter count**.

### Economic Formulation vs. Prioritization Model
We strictly distinguish between the two:
1. **Official Competition Scoring:** Evaluated as:
   $$\text{Scoring Cost} = (\text{Dispatched Visits} \times €380) + (\text{Unattended Faulty Gateways} \times €600)$$
   The official €600 penalty applies per faulty gateway regardless of meter count.
2. **Sentinel's Internal Prioritization:** Technical failure risk ($R_{\text{tech}}$) is scaled by installed meter count ($M_{\text{impact}}$) strictly for **rank ordering** among degraded gateways. A dead gateway serving 800 meters disrupts 5× more customer billing than one serving 40 meters. Meter count breaks ties and elevates high-blast-radius sites, but **never** pushes a healthy gateway into the top 15.

---

## Key Decisions & Why This Approach

| Decision | Chosen Approach | Rejected Alternative | Core Rationale |
| :--- | :--- | :--- | :--- |
| **Problem Definition** | Detect active, severe, persistent faults | Predictive failure forecasting | 60.7% of historical visits resulted in no defect found (high operational uncertainty); resolving deterministic, active outages yields immediate, provable operational value. |
| **Ranking Model** | Staged additive core + log impact multiplier | Unweighted 3-sigma flag counting | Reference baseline is blind to persistence (treats 15 1-min blips same as 15h dead) and ignores customer blast radius. |
| **Collinear Signals** | Use only `offline_duration_sec` | Include `disconnection_cnt` | `disconnection_cnt` and offline duration have $r=0.94$; counting both triple-counts socket reconnect flap. |
| **Algorithm Class** | Fixed engineering weights evaluated via backtesting | Supervised ML (XGBoost) | Only 642 historical visit labels exist with high bias; ML models risk severe drift on unseen telemetry partitions. |
| **Auxiliary Data** | Validation evidence only | Runtime scoring features | `meter_read_success.csv` ends on 2026-01-26 (5-week lag); `engineer_review` was created on 2026-02-14 (cannot leak backward). |
| **Live Rerun** | Dynamic glob scan on `/rerun` | Service restart on new data | Round Two drops new telemetry into `data/`; DuckDB re-evaluates filesystem globs dynamically with zero downtime. |
| **Part 2 Track** | Software Development (60%) | Data Science / Business Analytics | Real utility operations require dependable, tested, resilient services with clean contracts and live change readiness. |

*(Full rationale, alternatives, evidence, and falsification conditions are documented in [DECISIONS.md](DECISIONS.md)).*

---

## Architecture & Hexagonal Purity

Sentinel adheres to a strict **Hexagonal (Ports & Adapters) Architecture**, verified at build time by ArchUnit (`ArchitectureTest.java`):

```
┌──────────────────────────────────────────────────────────────────┐
│                            WEB LAYER                             │
│   PredictionController · DTOs · RFC-7807 GlobalExceptionHandler  │
└─────────────────────────────────┬────────────────────────────────┘
                                  │
┌─────────────────────────────────▼────────────────────────────────┐
│                        APPLICATION LAYER                         │
│   GatewayPredictionApplicationService · FeatureExtractionService │
│   DecisionExplanationService · PredictionsCsvExporter            │
│   In-Memory ConcurrentHashMap Cache (evicted on /rerun)          │
└─────────────────────────────────┬────────────────────────────────┘
                                  │
┌─────────────────────────────────▼────────────────────────────────┐
│                          DOMAIN LAYER                            │
│   Models: Gateway, GatewayId, PredictionWeek, BaselineStats      │
│   Ranking: RankingStrategy, RiskBasedRankingStrategy             │
│   Features: GatewayFeatures, FeatureComputer                     │
│   (Zero framework dependencies · Zero Spring or DB imports)      │
└─────────────────────────────────▲────────────────────────────────┘
                                  │
┌─────────────────────────────────┴────────────────────────────────┐
│                      INFRASTRUCTURE LAYER                        │
│   DuckDbTelemetryRepository · DuckDbGatewayRepository            │
│   CsvMeterReadRepository · DuckDbConfiguration                   │
│   (Implements Application Output Ports via In-Process DuckDB)    │
└──────────────────────────────────────────────────────────────────┘
```

---

## Ranking Method

The ranking engine evaluates candidates for target Monday $T_{\text{target}}$:

### 1. Technical Failure Intensity ($R_{\text{tech}}$)
$$R_{\text{tech}} = \left( 0.45 \cdot \tilde{F}_{01} + 0.35 \cdot \tilde{F}_{03} + 0.20 \cdot \tilde{F}_{02} \right) \times T_{\text{trend}}$$

- $\tilde{F}_{01} = \min(1.0, F_{01} / 84.0)$: Flagged Offline Hours normalized against a 3.5-day ceiling.
- $\tilde{F}_{03} = \min(1.0, F_{03} / 48.0)$: Max Consecutive Offline Run normalized against a 48-hour continuous outage. Complete outages had a **65.0% repair rate** in field history.
- $\tilde{F}_{02} = \min(1.0, F_{02} / 24.0)$: Flagged Reboot Hours normalized against 24 hours. Reboot clustering had a **56.4% repair rate** (power supply and antenna replacements).
- $T_{\text{trend}}$: Outage trajectory factor from $F_{04}$:
  - $1.2$ if accelerating ($F_{04} > 1.2$)
  - $0.8$ if self-healing / recovering ($F_{04} < 0.5$)
  - $1.0$ otherwise

### 2. Guarded Business Impact Scaling ($M_{\text{impact}}$)
$$\text{If } R_{\text{tech}} < 0.05: \quad \text{Score} = R_{\text{tech}} \quad (\text{fallback candidate, zero multiplier applied})$$
$$\text{If } R_{\text{tech}} \ge 0.05: \quad M_{\text{impact}} = \frac{\log_{10}(\max(10, F_{05}))}{\log_{10}(156)}$$
$$\text{Score} = R_{\text{tech}} \times M_{\text{impact}} \times 100.0$$

### 3. Strict 5-Tier Deterministic Tie-Breaking
1. `score` descending ($\epsilon = 10^{-6}$)
2. `f03MaxConsecutiveOfflineHours` descending
3. `f01FlaggedOfflineHours` descending
4. `f05MetersExposure` descending
5. `gatewayId` ascending (12-character canonical hex string)

---

## Explainability & Pairwise Comparison

Sentinel provides deterministic, feature-traceable explanations with **zero LLM hallucinations**:

- **Per-Gateway Explanation (`GET .../explain`):**
  - High-level operational summary.
  - Assigned risk level (`CRITICAL`, `HIGH`, `MODERATE`, `LOW`, `NONE`).
  - Decision category (`MISSING_TELEMETRY`, `ACTIONABLE_RISK`, `HIGH_PRIORITY`, `LOW_CONFIDENCE`, `FALLBACK`).
  - Individual feature contributions with human-readable severity tags (`F01` 42h `CRITICAL`, `F03` 48h `CRITICAL`).
  - RFC-4180 reason string ($\le 300$ chars) matching `predictions.csv`.
- **Pairwise Contrast (`GET .../compare/{otherId}`):**
  - Identifies score differential ($\Delta \text{score}$).
  - Explains the dominant feature why Gateway A outranked Gateway B.
  - Generates a **Proximity Warning** if $|\Delta \text{score}| < 5.0$, alerting dispatchers to marginal differences.

---

## Data Handling & Zero-Restart Rerun

- **Data Engine:** DuckDB in-process OLAP engine over Hive-partitioned Parquet (`data/telemetry/*/*.parquet`).
- **Dynamic Globbing:** DuckDB evaluates file system globs at query execution time.
- **Rerun Semantics (`POST /rerun`):** Evicts the in-memory cache entry for the specified week and executes a clean query scan over disk. When new telemetry partitions are dropped into `data/telemetry/` during a live session, calling `/rerun` immediately ingests the new data **without restarting Spring Boot**.
- **Regression Proved:** Covered by `RerunDynamicDataIntegrationTest.java`.

---

## Temporal Safety & Anti-Leakage Protocol

1. **Strict Window Boundaries:**
   - Baseline Window: $[T_{\text{target}} - 28\text{d}, T_{\text{target}})$
   - Scoring Window: $[T_{\text{target}} - 7\text{d}, T_{\text{target}})$
   - Exclusive upper bound: `ts_utc < ?` strictly excludes all observations on or after $T_{\text{target}}$.
2. **Auxiliary Data Isolation:**
   - `meter_read_success.csv` ends on 2026-01-26; used only for descriptive feature $F_{07}$, never for scoring.
   - `field_visits.csv` (ended 2026-02-14) and `engineer_review_2026-02.xlsx` (audited 2026-02-14) are strictly post-hoc evaluation references with zero runtime pipeline dependencies.
3. **Automated Verification:** Verified by `TemporalLeakageTest.java`.

---

## REST API Reference

Base URL: `http://localhost:8080/api/v1`

### Endpoints

| Method | Path | Description | Status Codes |
| :--- | :--- | :--- | :--- |
| `GET` | `/predictions/{week}` | Top 15 ranked visits for target week | `200`, `400`, `422` |
| `GET` | `/predictions/{week}/{gatewayId}/explain` | Structural decision explanation | `200`, `400`, `404`, `422` |
| `GET` | `/predictions/{week}/{idA}/compare/{idB}` | Pairwise ranking contrast | `200`, `400`, `404`, `422` |
| `POST`| `/predictions/{week}/rerun` | Invalidate cache & recompute from source data | `200`, `400`, `422` |
| `GET` | `/actuator/health` | Service liveness & data availability indicator | `200`, `503` |

### Supported Week Formats
- ISO Date: `2026-03-09` (must be a Monday)
- ISO Week: `2026-W11`

### Deliberate Error Handling (RFC-7807)
- `400 MALFORMED_WEEK`: Date cannot be parsed.
- `400 INVALID_WEEK_DAY`: Date is not a Monday.
- `400 MALFORMED_GATEWAY_ID`: Not a valid 12-hex or 17-colon MAC.
- `400 INVALID_COMPARISON`: Comparing a gateway to itself.
- `404 GATEWAY_NOT_FOUND`: Gateway is not in the top 15 recommendations.
- `422 UNSUPPORTED_WEEK`: Week is before `2025-09-01` (insufficient 28d baseline).
- `422 FUTURE_WEEK`: Week is beyond telemetry coverage (`> 2026-03-30`).

---

## Operator Decision Console

Sentinel includes a high-density, production-grade decision console served at `/`:
- **Light / Dark Enterprise Themes:** High-contrast neutral palette (Slate/Zinc) with operational semantic colors (Emerald, Amber, Rose, Cyan).
- **Priority #1 Command Card:** Immediate 5-second clarity on the single most urgent dispatch candidate.
- **Full Work Queue:** 15-gateway table with severity badges, metrics, and instant modal drill-down.
- **Pairwise Comparator Panel:** Interactive selector contrasting any two gateways with feature differentials.
- **Zero Build / Zero Dependencies:** Single static file (`src/main/resources/static/index.html`), 100% offline, zero npm, zero external CDNs.

### Offline Operation

Once the application and its dependencies are available locally, Sentinel's core decision workflow does not require runtime Internet connectivity or external cloud services. Challenge data is processed locally, and the operator console is served directly by the Spring Boot application:
- **Local Telemetry & Reference Ingestion:** All raw telemetry partitions (`telemetry/month=*/*.parquet`) and metadata files (`gateway_master.csv`, etc.) are read directly from the local `./data` directory with zero network transfers.
- **Embedded In-Process Analytics:** Analytical feature queries and rolling aggregations execute in-process via embedded DuckDB with zero external database connections or cloud endpoints.
- **Self-Contained Operator Console:** The web UI is served directly from Spring Boot static resources (`src/main/resources/static/index.html`) with zero runtime external CDN links, web fonts, or remote JavaScript/CSS libraries.
- **Isolated Decision Loop:** Ranking computation, explanation generation, pairwise comparisons, and on-demand `/rerun` recomputations operate entirely within the local host boundary.

---

## Docker Deployment

> **Deployment Status:** Static Docker configuration inspection completed; runtime execution not tested because Docker Engine was unavailable.

Native execution (`./mvnw spring-boot:run` or `.\mvnw.cmd spring-boot:run`) is the verified primary run path. Docker Compose is provided as optional, supporting containerized infrastructure:

```bash
# Build and start container
docker compose up --build

# Run in background
docker compose up -d

# View logs
docker compose logs -f

# Stop container
docker compose down
```

### Container Safeguards
- Non-root user `sentinel` (UID 10001).
- Host data mounted **read-only** (`./data:/app/data:ro`).
- Built-in `HEALTHCHECK` probing `/actuator/health`.
- `.dockerignore` prevents host challenge data from leaking into the container image.

---

## Testing & Quality Gates

Sentinel enforces comprehensive automated test verification:

```bash
# Run all unit, integration, and architecture tests
./mvnw clean test

# Check Spotless formatting
./mvnw spotless:check

# Apply Spotless auto-formatting
./mvnw spotless:apply
```

### Verified Test Suite (213 Tests, 0 Failures, 0 Errors)
- **Execution Truth:** 213 automated test executions across 28 test classes (193 distinct test definitions, including 5 ArchUnit rules and 25 repeated determinism test runs).
- **Unit Tests:** Individual feature computers ($F_{01}–F_{06}$), statistical calculations, score scaling, comparators, parsers.
- **Integration Tests:** DuckDB parquet reader, CSV exporter, REST API MockMvc controllers, static resource & favicon serving.
- **End-to-End Tests (`PredictionApiE2EIntegrationTest`):** Full-path execution against real DuckDB parquet telemetry.
- **Regression Tests (`DuckDbTelemetryRepositoryRegressionTest`):** Hive partition globbing and timestamp comparison bug fixes.
- **Live Session Tests (`RerunDynamicDataIntegrationTest`):** Proves new partition discovery on `/rerun` without JVM restart.
- **CSV Gate Tests (`PredictionsCsvGenerationTest`):** Validates 120-row `predictions.csv` generation.
- **Architecture Tests (`ArchitectureTest`):** 5/5 ArchUnit rules enforcing strict layer isolation.

---

## Validation & Historical Backtesting

Sentinel's scoring engine uses **fixed engineering weights evaluated through walk-forward historical backtesting** across 11 historical validation weeks spanning 8 months of telemetry (`BACKTESTING.md`):

| Metric | Reference Baseline | Sentinel Risk-Based | Operational Contrast |
| :--- | :--- | :--- | :--- |
| **Confirmed Repairs (`Fehler behoben`)** | 15 repairs | **23 repairs** | **+53.3% confirmed repairs** |
| **Visits with No Defect Found (`Kein Fehler`)** | 7 visits | **7 visits** | Equivalent precision |
| **Engineer Review Match (`Schlecht`)** | 27 gateways | **39 gateways** | **+44.4% expert agreement** |
| **Total Impacted Meters Prioritized** | — | **35,968 meters** | High customer blast radius |
| **Illustrative Outage Penalty Avoidance** | Base | **+€19,200** | Model assumption (4-wk persistence) |

> **Operational Evidence Note:**  
> Across 11 historical walk-forward validation weeks, Sentinel selected 23 gateways associated with confirmed repairs versus 15 selected by the baseline (+53.3%). This is evidence from historical validation, not a claim of realized financial savings.  
> Furthermore, 60.7% of historical field visits resulted in no defect being found (`Kein Fehler gefunden`), reflecting high operational uncertainty in past human dispatching rather than proof that the initial alert was false.

---

## Limitations — What Sentinel Cannot Do

1. **Telemetry & Data-Pipeline Outages:** An upstream failure in telemetry collection produces an absence of records that can resemble a physical gateway failure. Sentinel flags communications distress, but cannot distinguish an ingestion pipeline outage from a physical gateway failure without out-of-band pipeline health checks.
2. **Facility-Wide Power & Cellular Carrier Outages:** A regional cellular tower (BTS) outage or building power cut takes multiple gateways and meters offline simultaneously. Sentinel detects technical distress on individual gateways, but requires external grid topology to identify regional common-mode failures.
3. **Unlabeled & Unvisited Gateways Are Not Automatically Healthy:** Technician visits are capped at 15 per week. Unvisited gateways (~285 units) are not inspected; absence of a service ticket does not prove operational health.
4. **Historical Field Visits Suffer Selection Bias:** Past work orders reflect where human operators chose to send technicians. The 60.7% no-defect rate reflects historical dispatch uncertainty rather than an unbiased random fleet trial.
5. **Meter-Read Success Ingestion Lag:** Auxiliary meter success data terminates on 2026-01-26 (5-week lag for March 2026 predictions). It is quarantined strictly to descriptive context ($F_{07}$) and never used for active scoring to avoid temporal leakage.
6. **Deterministic Decision Support vs. Physical Proof:** Sentinel is an operational decision-support tool that prioritizes technician dispatch under capacity constraints. It does not provide physical proof of hardware failure prior to on-site inspection.

*(See [LIMITATIONS.md](LIMITATIONS.md) for complete technical constraints).*

---

## AI Usage Disclosure

All development adhered to transparent human-in-the-loop engineering. AI tooling (Antigravity AI) was utilized for scaffolding, test construction, and documentation. All architectural decisions, mathematical formulations, economic tradeoffs, and temporal boundaries were human-directed and verified. *(See [AI-USAGE.md](AI-USAGE.md) for phase-by-phase breakdown).*

---

## Project Structure

```
sentinel/
├── pom.xml                               # Maven build configuration
├── mvnw / mvnw.cmd                       # Maven wrappers (Linux / Windows)
├── compose.yaml                          # Docker Compose configuration
├── 23091A05P7.pdf                        # Official Registration ID resume attachment
├── predictions.csv                       # NEXORA Part 1 Gate artifact (120 rows)
├── README.md                             # Evaluator-first documentation
├── DECISIONS.md                          # 25% Judgement Architecture Decision Records
├── LIMITATIONS.md                        # Known constraints & out-of-scope items
├── AI-USAGE.md                           # AI tool disclosure & oversight record
├── docs/
│   └── BACKTESTING.md                    # 11-week historical calibration report
├── docker/
│   ├── Dockerfile                        # Multi-stage distroless-style build
│   └── docker-compose.yml                # Secondary compose spec
├── src/
│   ├── main/
│   │   ├── java/com/lpdg/sentinel/
│   │   │   ├── application/              # Services, ports, explanation, CSV export
│   │   │   ├── domain/                   # Pure business models, features, ranking
│   │   │   ├── infrastructure/           # DuckDB, CSV, configuration adapters
│   │   │   └── web/                      # REST controllers, DTOs, RFC-7807 errors
│   │   └── resources/
│   │       ├── application.yml           # Externalized configuration properties
│   │       └── static/index.html         # Single-file Operator Decision Console
│   └── test/                             # 213 automated tests (Unit, E2E, ArchUnit)
```
