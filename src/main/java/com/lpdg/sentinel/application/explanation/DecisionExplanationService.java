package com.lpdg.sentinel.application.explanation;

import com.lpdg.sentinel.domain.explanation.DecisionCategory;
import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.explanation.FeatureContribution;
import com.lpdg.sentinel.domain.explanation.RiskLevel;
import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.ranking.RankedGatewayDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateless application service that builds structured, traceable decision explanations.
 *
 * <p>Every explanation field is rooted in observable feature values, documented ranking policies,
 * or explicit data confidence states — no free-form text generation is performed.
 *
 * <h3>Design Constraints</h3>
 * <ul>
 *   <li>R_tech is recomputed from feature values using the same deterministic formula as
 *       {@code RiskBasedRankingStrategy} — not reverse-engineered from final score.</li>
 *   <li>No external dependencies; deterministic output for same inputs.</li>
 *   <li>csvReason is always &le; 300 characters and assembles only from active signals.</li>
 * </ul>
 *
 * <p>Normalization denominators: F01=84h, F03=48h, F02=24h. Fleet median meters=156.
 */
public class DecisionExplanationService {

    // Normalization denominators matching RiskBasedRankingStrategy exactly
    private static final double F01_NORM = 84.0;
    private static final double F02_NORM = 24.0;
    private static final double F03_NORM = 48.0;
    private static final double FLEET_MEDIAN_METERS = 156.0;
    private static final double ACTIONABLE_RISK_THRESHOLD = 0.05;

    /**
     * Builds a {@link DecisionExplanation} for the given ranked gateway decision.
     *
     * @param features the gateway feature vector used in scoring
     * @param decision the ranked gateway decision produced by the ranking strategy
     * @return complete decision explanation with all traceable evidence
     * @throws NullPointerException if either argument is null
     */
    public DecisionExplanation explain(GatewayFeatures features, RankedGatewayDecision decision) {
        Objects.requireNonNull(features, "features must not be null");
        Objects.requireNonNull(decision, "decision must not be null");

        if (!features.gatewayId().equals(decision.gatewayId())) {
            throw new IllegalArgumentException(
                    "features.gatewayId (" + features.gatewayId() + ") must match decision.gatewayId ("
                            + decision.gatewayId() + ")");
        }

        // Recompute R_tech from feature values (same formula as RiskBasedRankingStrategy)
        double rTech = computeRTech(features);

        DecisionCategory category = deriveCategory(features, decision, rTech);
        RiskLevel riskLevel = deriveRiskLevel(features, rTech);
        List<FeatureContribution> contributions = buildContributions(features, rTech);
        List<String> warnings = buildWarnings(features);
        List<String> limitations = buildLimitations(features, rTech);
        String csvReason = buildCsvReason(features, decision, category, rTech);
        String summary = buildSummary(features, decision, category, riskLevel, rTech);

        return new DecisionExplanation(
                decision.rank(),
                decision.gatewayId(),
                decision.score(),
                decision.fallback(),
                category,
                riskLevel,
                contributions,
                features.dataConfidence(),
                warnings,
                limitations,
                csvReason,
                summary);
    }

    // ── R_tech recomputation ────────────────────────────────────────────────

    static double computeRTech(GatewayFeatures gf) {
        if (gf.dataConfidence() == DataConfidence.MISSING_TELEMETRY) {
            // Missing telemetry bypasses normal R_tech; flagged separately
            return 1.0;
        }
        double offlineNorm = Math.min(1.0, gf.f01FlaggedOfflineHours() / F01_NORM);
        double persistNorm = Math.min(1.0, gf.f03MaxConsecutiveOfflineHours() / F03_NORM);
        double rebootNorm = Math.min(1.0, gf.f02FlaggedRebootHours() / F02_NORM);
        double trendFactor = computeTrendFactor(gf.f04TrendTrajectoryRatio());
        return (0.45 * offlineNorm + 0.35 * persistNorm + 0.20 * rebootNorm) * trendFactor;
    }

    static double computeTrendFactor(double f04) {
        if (f04 > 1.2) return 1.2;
        if (f04 < 0.5) return 0.8;
        return 1.0;
    }

    // ── Category derivation (deterministic priority) ────────────────────────

    static DecisionCategory deriveCategory(
            GatewayFeatures features, RankedGatewayDecision decision, double rTech) {
        if (features.dataConfidence() == DataConfidence.MISSING_TELEMETRY) {
            return DecisionCategory.MISSING_TELEMETRY;
        }
        if (decision.fallback()) {
            return DecisionCategory.FALLBACK;
        }
        if (features.dataConfidence() == DataConfidence.LOW_HISTORY) {
            return DecisionCategory.LOW_CONFIDENCE;
        }
        if (rTech >= SeverityThresholds.TECH_RISK_CRITICAL) {
            return DecisionCategory.ACTIONABLE_RISK;
        }
        return DecisionCategory.HIGH_PRIORITY;
    }

    // ── Risk level derivation ───────────────────────────────────────────────

    static RiskLevel deriveRiskLevel(GatewayFeatures features, double rTech) {
        if (features.dataConfidence() == DataConfidence.MISSING_TELEMETRY) {
            return RiskLevel.CRITICAL;
        }
        if (features.f03MaxConsecutiveOfflineHours() >= SeverityThresholds.F03_CRITICAL) {
            return RiskLevel.CRITICAL;
        }
        if (features.f01FlaggedOfflineHours() >= SeverityThresholds.F01_HIGH
                || rTech >= SeverityThresholds.TECH_RISK_HIGH) {
            return RiskLevel.HIGH;
        }
        if (features.f01FlaggedOfflineHours() >= SeverityThresholds.F01_MODERATE
                || rTech >= SeverityThresholds.TECH_RISK_MODERATE) {
            return RiskLevel.MODERATE;
        }
        if (rTech >= SeverityThresholds.TECH_RISK_ACTIONABLE) {
            return RiskLevel.LOW;
        }
        return RiskLevel.NONE;
    }

    // ── Feature contributions ───────────────────────────────────────────────

    private static List<FeatureContribution> buildContributions(GatewayFeatures gf, double rTech) {
        List<FeatureContribution> out = new ArrayList<>();

        int f01 = gf.f01FlaggedOfflineHours();
        out.add(new FeatureContribution(
                "F01",
                "Flagged Offline Hours",
                f01 + "h",
                f01SeverityLabel(f01),
                f01 >= SeverityThresholds.F01_LOW));

        int f02 = gf.f02FlaggedRebootHours();
        out.add(new FeatureContribution(
                "F02",
                "Flagged Reboot Hours",
                f02 + "h",
                f02SeverityLabel(f02),
                f02 >= SeverityThresholds.F02_LOW));

        int f03 = gf.f03MaxConsecutiveOfflineHours();
        out.add(new FeatureContribution(
                "F03",
                "Max Consecutive Offline Hours",
                f03 + "h",
                f03SeverityLabel(f03),
                f03 >= SeverityThresholds.F03_LOW));

        double f04 = gf.f04TrendTrajectoryRatio();
        out.add(new FeatureContribution(
                "F04",
                "Outage Trajectory Ratio",
                String.format("%.2fx", f04),
                f04SeverityLabel(f04),
                f04 > SeverityThresholds.F04_DETERIORATING || f04 < SeverityThresholds.F04_RECOVERING));

        int f05 = gf.f05MetersExposure();
        out.add(new FeatureContribution(
                "F05",
                "Installed Meters",
                f05 + " meters",
                f05SeverityLabel(f05),
                rTech >= ACTIONABLE_RISK_THRESHOLD));

        return out;
    }

    private static String f01SeverityLabel(int h) {
        if (h >= SeverityThresholds.F01_CRITICAL) return "CRITICAL";
        if (h >= SeverityThresholds.F01_HIGH) return "HIGH";
        if (h >= SeverityThresholds.F01_MODERATE) return "MODERATE";
        if (h >= SeverityThresholds.F01_LOW) return "LOW";
        return "NOMINAL";
    }

    private static String f02SeverityLabel(int h) {
        if (h >= SeverityThresholds.F02_CRITICAL) return "CRITICAL";
        if (h >= SeverityThresholds.F02_HIGH) return "HIGH";
        if (h >= SeverityThresholds.F02_MODERATE) return "MODERATE";
        if (h >= SeverityThresholds.F02_LOW) return "LOW";
        return "NOMINAL";
    }

    private static String f03SeverityLabel(int h) {
        if (h >= SeverityThresholds.F03_CRITICAL) return "CRITICAL";
        if (h >= SeverityThresholds.F03_HIGH) return "HIGH";
        if (h >= SeverityThresholds.F03_MODERATE) return "MODERATE";
        if (h >= SeverityThresholds.F03_LOW) return "LOW";
        return "NOMINAL";
    }

    private static String f04SeverityLabel(double ratio) {
        if (ratio > SeverityThresholds.F04_ACCELERATING) return "ACCELERATING";
        if (ratio > SeverityThresholds.F04_DETERIORATING) return "DETERIORATING";
        if (ratio < SeverityThresholds.F04_RECOVERING) return "RECOVERING";
        return "STABLE";
    }

    private static String f05SeverityLabel(int meters) {
        if (meters > SeverityThresholds.F05_CRITICAL) return "CRITICAL";
        if (meters > SeverityThresholds.F05_HIGH) return "HIGH";
        if (meters >= SeverityThresholds.F05_MODERATE) return "MODERATE";
        return "LOW";
    }

    // ── Warnings ────────────────────────────────────────────────────────────

    private static List<String> buildWarnings(GatewayFeatures gf) {
        List<String> warnings = new ArrayList<>();
        if (gf.dataConfidence() == DataConfidence.MISSING_TELEMETRY) {
            warnings.add("No telemetry received across scoring week; all feature values are zero by default.");
        }
        if (gf.dataConfidence() == DataConfidence.LOW_HISTORY) {
            warnings.add("Baseline computed from < 28 days of history; statistical thresholds may be imprecise.");
        }
        if (gf.f07HistoricReadSuccess() < 80.0) {
            warnings.add(String.format(
                    "Historic meter read success rate is low (%.1f%%); repeated failed reads may indicate communications issues.",
                    gf.f07HistoricReadSuccess()));
        }
        if (gf.f08DisconnectionFrequency() > 20) {
            warnings.add(String.format(
                    "High disconnection frequency (%d) observed in scoring week (collinear with F01).",
                    gf.f08DisconnectionFrequency()));
        }
        return warnings;
    }

    // ── Limitations ─────────────────────────────────────────────────────────

    private static List<String> buildLimitations(GatewayFeatures gf, double rTech) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Scoring reflects telemetry from a single 7-day window; chronic intermittent faults may be underrepresented.");
        limitations.add("Root cause (power, communications, or hardware) cannot be distinguished from telemetry alone.");
        if (rTech < SeverityThresholds.TECH_RISK_ACTIONABLE) {
            limitations.add("Technical risk is below actionable threshold; visit included for capacity only.");
        }
        return limitations;
    }

    // ── CSV Reason assembly ─────────────────────────────────────────────────

    static String buildCsvReason(
            GatewayFeatures gf,
            RankedGatewayDecision decision,
            DecisionCategory category,
            double rTech) {

        String raw = switch (category) {
            case MISSING_TELEMETRY -> String.format(
                    "No telemetry received in scoring week; communications failure cannot be excluded. "
                            + "Gateway active in inventory. %d meters potentially affected.",
                    gf.f05MetersExposure());
            case FALLBACK -> String.format(
                    "No significant technical distress detected (risk score: %.2f). "
                            + "Included to complete weekly visit budget. %d meters installed.",
                    decision.score(), gf.f05MetersExposure());
            default -> buildRiskCsvReason(gf, rTech);
        };

        // Hard truncate at 300 chars to satisfy CSV length constraint
        if (raw.length() > DecisionExplanation.MAX_CSV_REASON_LENGTH) {
            return raw.substring(0, DecisionExplanation.MAX_CSV_REASON_LENGTH - 3) + "...";
        }
        return raw;
    }

    private static String buildRiskCsvReason(GatewayFeatures gf, double rTech) {
        List<String> signals = new ArrayList<>();

        // Primary signal: highest-severity active feature
        if (gf.f03MaxConsecutiveOfflineHours() >= SeverityThresholds.F03_CRITICAL) {
            signals.add(gf.f03MaxConsecutiveOfflineHours() + "h continuous outage");
        }
        if (gf.f01FlaggedOfflineHours() >= SeverityThresholds.F01_MODERATE) {
            signals.add(gf.f01FlaggedOfflineHours() + " offline anomaly hours");
        }
        if (gf.f02FlaggedRebootHours() >= SeverityThresholds.F02_MODERATE) {
            signals.add(gf.f02FlaggedRebootHours() + "h reboot distress");
        }
        if (signals.isEmpty() && gf.f01FlaggedOfflineHours() >= SeverityThresholds.F01_LOW) {
            signals.add(gf.f01FlaggedOfflineHours() + "h offline anomalies");
        }

        // Trend signal (only if non-stable)
        double f04 = gf.f04TrendTrajectoryRatio();
        String trendLabel = f04SeverityLabel(f04);
        if (!trendLabel.equals("STABLE")) {
            signals.add("recent outage is " + trendLabel.toLowerCase());
        }

        String signalPart = String.join("; ", signals);
        return (signalPart.isEmpty() ? "Detectable technical distress" : signalPart)
                + ". " + gf.f05MetersExposure() + " meters affected.";
    }

    // ── Summary assembly ────────────────────────────────────────────────────

    private static String buildSummary(
            GatewayFeatures gf,
            RankedGatewayDecision decision,
            DecisionCategory category,
            RiskLevel riskLevel,
            double rTech) {

        return switch (category) {
            case MISSING_TELEMETRY -> String.format(
                    "Rank #%d | MISSING TELEMETRY — No telemetry received during the scoring week. "
                            + "A complete communications or power failure cannot be excluded. "
                            + "%d meters potentially without data coverage. "
                            + "Gateway is active in the master inventory. Score: %.2f.",
                    decision.rank(), gf.f05MetersExposure(), decision.score());

            case FALLBACK -> String.format(
                    "Rank #%d | FALLBACK — No actionable technical distress detected "
                            + "(technical risk: %.4f, threshold: 0.05). "
                            + "Included to satisfy weekly visit capacity. %d meters installed. "
                            + "Score: %.2f.",
                    decision.rank(), rTech, gf.f05MetersExposure(), decision.score());

            case LOW_CONFIDENCE -> String.format(
                    "Rank #%d | LOW CONFIDENCE — Gateway has insufficient historical baseline "
                            + "(< 28 days). Statistical thresholds may be imprecise. "
                            + "F01: %dh, F02: %dh, F03: %dh, F04: %.2fx, meters: %d. "
                            + "Risk level: %s. Score: %.2f.",
                    decision.rank(),
                    gf.f01FlaggedOfflineHours(), gf.f02FlaggedRebootHours(),
                    gf.f03MaxConsecutiveOfflineHours(), gf.f04TrendTrajectoryRatio(),
                    gf.f05MetersExposure(), riskLevel.name(), decision.score());

            default -> buildRiskSummary(gf, decision, riskLevel, rTech);
        };
    }

    private static String buildRiskSummary(
            GatewayFeatures gf,
            RankedGatewayDecision decision,
            RiskLevel riskLevel,
            double rTech) {
        String trajectoryDescription;
        double f04 = gf.f04TrendTrajectoryRatio();
        if (f04 > SeverityThresholds.F04_ACCELERATING) {
            trajectoryDescription = "accelerating (ratio: " + String.format("%.2f", f04) + ")";
        } else if (f04 > SeverityThresholds.F04_DETERIORATING) {
            trajectoryDescription = "deteriorating (ratio: " + String.format("%.2f", f04) + ")";
        } else if (f04 < SeverityThresholds.F04_RECOVERING) {
            trajectoryDescription = "recovering (ratio: " + String.format("%.2f", f04) + ")";
        } else {
            trajectoryDescription = "stable (ratio: " + String.format("%.2f", f04) + ")";
        }

        return String.format(
                "Rank #%d | %s RISK — Technical risk R_tech=%.4f. "
                        + "F01 (offline anomalies): %dh %s; "
                        + "F02 (reboot distress): %dh %s; "
                        + "F03 (continuous outage): %dh %s; "
                        + "F04 (trajectory): %s; "
                        + "F05 (meters): %d. "
                        + "Score: %.2f.",
                decision.rank(),
                riskLevel.name(),
                rTech,
                gf.f01FlaggedOfflineHours(), f01SeverityLabel(gf.f01FlaggedOfflineHours()),
                gf.f02FlaggedRebootHours(), f02SeverityLabel(gf.f02FlaggedRebootHours()),
                gf.f03MaxConsecutiveOfflineHours(), f03SeverityLabel(gf.f03MaxConsecutiveOfflineHours()),
                trajectoryDescription,
                gf.f05MetersExposure(),
                decision.score());
    }
}
