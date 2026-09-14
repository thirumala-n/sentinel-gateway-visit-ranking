# Phase 5 — Explainability & Decision Evidence
# SENTINEL — NEXORA 2026 · LPDG × RGM Innovation Challenge

## Purpose

This document defines the Phase 5 explanation architecture: how every `RankedGatewayDecision` is transformed into structured, auditable, traceable decision evidence.

Every statement in a generated explanation is grounded in:
- An observable feature value (F01–F08)
- A documented threshold or policy
- A data confidence state (`DataConfidence`)
- The ranking score output

No ungrounded natural-language generation is performed.

---

## Architecture Overview

```
GatewayFeatures + RankedGatewayDecision
        │
        ▼
DecisionExplanationService  (application layer)
        │
        ▼
DecisionExplanation  (domain record)
   ├── rank, gatewayId, score, fallback
   ├── DecisionCategory
   ├── RiskLevel
   ├── List<FeatureContribution>  (F01..F05)
   ├── DataConfidence
   ├── List<String> warnings
   ├── List<String> limitations
   ├── String csvReason  (≤ 300 chars)
   └── String summary
```

---

## DecisionCategory Logic

Categories are assigned in the following strict priority order:

| Priority | Condition | Category |
|:---|:---|:---|
| 1 | `dataConfidence == MISSING_TELEMETRY` | `MISSING_TELEMETRY` |
| 2 | `fallback == true` | `FALLBACK` |
| 3 | `dataConfidence == LOW_HISTORY` | `LOW_CONFIDENCE` |
| 4 | `R_tech >= 0.50` | `ACTIONABLE_RISK` |
| 5 | otherwise | `HIGH_PRIORITY` |

---

## RiskLevel Derivation

| RiskLevel | Primary Condition |
|:---|:---|
| `CRITICAL` | `MISSING_TELEMETRY` OR `F03 >= 24h` |
| `HIGH` | `F01 >= 20h` OR `R_tech >= 0.35` |
| `MODERATE` | `F01 >= 8h` OR `R_tech >= 0.15` |
| `LOW` | `R_tech >= 0.05` |
| `NONE` | `R_tech < 0.05` (fallback territory) |

---

## Feature Contribution Severity Labels

All thresholds are derived directly from the normalization denominators in `RiskBasedRankingStrategy`.

### F01 — Flagged Offline Hours (normalized to 84h)

| Severity | Range |
|:---|:---|
| `NOMINAL` | 0h |
| `LOW` | 1–7h |
| `MODERATE` | 8–19h |
| `HIGH` | 20–41h |
| `CRITICAL` | ≥ 42h (≥ 50% of saturation point) |

### F02 — Flagged Reboot Hours (normalized to 24h)

| Severity | Range |
|:---|:---|
| `NOMINAL` | 0h |
| `LOW` | 1–3h |
| `MODERATE` | 4–7h |
| `HIGH` | 8–11h |
| `CRITICAL` | ≥ 12h (≥ 50% of saturation point) |

### F03 — Max Consecutive Offline Hours (normalized to 48h)

| Severity | Range |
|:---|:---|
| `NOMINAL` | 0h |
| `LOW` | 1–5h |
| `MODERATE` | 6–11h |
| `HIGH` | 12–23h |
| `CRITICAL` | ≥ 24h (≥ 50% of saturation point — "sustained outage" as per ADR-012) |

### F04 — Outage Trajectory Ratio

| Label | Condition |
|:---|:---|
| `RECOVERING` | ratio < 0.5 |
| `STABLE` | 0.5 ≤ ratio ≤ 1.2 |
| `DETERIORATING` | 1.2 < ratio ≤ 2.5 |
| `ACCELERATING` | ratio > 2.5 |

These align directly with `trendFactor` breakpoints: 0.5 (recovering), 1.0 (stable), 1.2 (deteriorating).

### F05 — Installed Meter Count

| Severity | Range |
|:---|:---|
| `LOW` | < 80 meters |
| `MODERATE` | 80–200 meters |
| `HIGH` | 201–400 meters |
| `CRITICAL` | > 400 meters |

F05 is `active` when `R_tech >= 0.05` (i.e., when meter scaling actually contributes to the score).

---

## R_tech Recomputation

`DecisionExplanationService` recomputes R_tech from feature values using the **identical formula** as `RiskBasedRankingStrategy`:

```
R_tech = (0.45 * min(1, F01/84) + 0.35 * min(1, F03/48) + 0.20 * min(1, F02/24)) * T_trend

T_trend = 1.2  if F04 > 1.2   (deteriorating)
        = 0.8  if F04 < 0.5   (recovering)
        = 1.0  otherwise
```

R_tech is **not** stored in `RankedGatewayDecision` and is **not** reverse-engineered from the final score. This guarantees explanations are rooted in actual feature evidence.

---

## csvReason Templates (≤ 300 chars)

### MISSING_TELEMETRY

```
No telemetry received in scoring week; communications failure cannot be excluded. Gateway active in inventory. {meters} meters potentially affected.
```

### FALLBACK

```
No significant technical distress detected (risk score: {score:.2f}). Included to complete weekly visit budget. {meters} meters installed.
```

### ACTIONABLE_RISK / HIGH_PRIORITY

Assembled from active signals only:

```
{primary_signal}[; {secondary_signal}][; recent outage is {trend_label}]. {meters} meters affected.
```

Examples:
- `"24h continuous outage; 36 offline anomaly hours; recent outage is deteriorating. 312 meters affected."`
- `"22 offline anomaly hours; 8h reboot distress. 156 meters affected."`
- `"6h offline anomalies; recent outage is recovering. 422 meters affected."`

---

## Pairwise Comparison Report

`RankingComparisonReport.compare(higher, lower)` generates feature-referenced reasons explaining why `higher` ranks above `lower`:

1. **Category difference** — if categories differ, this is the primary reason
2. **F03 comparison** — continuous outage hours compared
3. **F01 comparison** — offline anomaly volume compared
4. **Score context** — score values always included as context

**Score proximity warning**: If `|scoreA - scoreB| < 5.0`, a warning note is added:
```
Scores are close (A: 87.30, B: 84.10); ordering may be sensitive to minor telemetry changes.
```

---

## Warnings and Limitations

### Warnings (data quality caveats)
- Emitted for `MISSING_TELEMETRY`: "No telemetry received across scoring week; all feature values are zero by default."
- Emitted for `LOW_HISTORY`: "Baseline computed from < 28 days of history; statistical thresholds may be imprecise."
- Emitted when `F07 < 80.0%`: historic read success rate anomaly
- Emitted when `F08 > 20`: high disconnection frequency

### Limitations (domain sensing boundaries)
- Always emitted: scoring reflects a single 7-day window
- Always emitted: root cause cannot be distinguished from telemetry alone
- Emitted for fallback candidates: technical risk below actionable threshold

---

## Test Coverage

| Test class | Tests | Purpose |
|:---|:---|:---|
| `DecisionExplanationServiceTest` | 21 | All explanation paths, category/risk/label derivation, validation |
| `ExplanationAdversarialTest` | 5 | Healthy high-meter, severe low-meter, missing telemetry, threshold boundaries |
| `ExplanationDeterminismTest` | 28 | Repeated-invocation string equality, comparison report determinism |

**Total Phase 5 tests: 54** (179 total including all prior phases)

---

## Design Rationale

All threshold values in `SeverityThresholds` are **not invented** — they are derived from:
- Normalization denominators in `RiskBasedRankingStrategy` (F01=84h, F02=24h, F03=48h)
- ≥ 50% saturation rule (e.g., CRITICAL at 42h for F01 because 42 = 84 × 0.5)
- `ACTIONABLE_RISK_THRESHOLD = 0.05` from `RiskBasedRankingStrategy.ACTIONABLE_RISK_THRESHOLD`
- ADR-012 "sustained outage" definition (F03 ≥ 24h)

Full architecture decision documented in **ADR-013** in `DECISIONS.md`.
