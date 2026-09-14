package com.lpdg.sentinel.domain.ranking;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.DataConfidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Competition-grade risk-based ranking strategy calibrated against historical operational evidence.
 *
 * <p>Integrates orthogonal technical failure signals (offline persistence, anomaly frequency,
 * reboot distress, and trajectory trend) scaled by customer/meter blast radius without allowing
 * meter count alone to trigger false-alarm visits on healthy units.
 *
 * <h3>Mathematical Formulation</h3>
 *
 * <pre>
 *   R_tech = (0.45 * (F01 / 84) + 0.35 * (F03 / 48) + 0.20 * (F02 / 24)) * T_trend
 *
 *   T_trend = 1.2 if F04 > 1.2 (deteriorating)
 *           = 0.8 if F04 < 0.5 (recovering)
 *           = 1.0 otherwise
 *
 *   Score = R_tech * (log10(max(10, F05)) / log10(156)) * 100.0   [if R_tech >= 0.05]
 *         = R_tech                                                [if R_tech < 0.05, marked fallback]
 * </pre>
 *
 * <h3>Tie-Breaking Hierarchy</h3>
 *
 * <ol>
 *   <li>Score descending (tolerance 1e-6)
 *   <li>F03 Max Consecutive Offline Hours descending (unbroken persistence prioritized)
 *   <li>F01 Flagged Offline Hours descending (aggregate anomaly volume)
 *   <li>F05 Meters Exposure descending (customer blast radius)
 *   <li>GatewayId ascending (lexicographical string sort as ultimate deterministic tie-breaker)
 * </ol>
 */
public class RiskBasedRankingStrategy implements RankingStrategy {

    /** Fleet median installed meter benchmark. */
    public static final double FLEET_MEDIAN_METERS = 156.0;

    /** Minimum technical risk required to be considered an actionable failure. */
    public static final double ACTIONABLE_RISK_THRESHOLD = 0.05;

    private static final double EPSILON = 1e-6;

    @Override
    public List<RankedGatewayDecision> rank(RankingContext context, int topN) {
        Objects.requireNonNull(context, "context must not be null");
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be >= 1, got: " + topN);
        }

        List<ScoredCandidate> candidates = new ArrayList<>(context.size());
        for (GatewayFeatures gf : context.gatewayFeatures()) {
            candidates.add(scoreCandidate(gf));
        }

        candidates.sort(RiskBasedRankingStrategy::compareCandidates);

        int limit = Math.min(topN, candidates.size());
        List<RankedGatewayDecision> decisions = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            ScoredCandidate sc = candidates.get(i);
            decisions.add(
                    new RankedGatewayDecision(
                            sc.features.gatewayId(),
                            i + 1,
                            sc.score,
                            sc.isFallback,
                            sc.rationale));
        }

        return List.copyOf(decisions);
    }

    private ScoredCandidate scoreCandidate(GatewayFeatures gf) {
        // Missing telemetry handling: complete communication loss
        if (gf.dataConfidence() == DataConfidence.MISSING_TELEMETRY) {
            double meterMultiplier = computeMeterMultiplier(gf.f05MetersExposure());
            double score = 100.0 * meterMultiplier;
            String rationale =
                    String.format(
                            "Missing telemetry: presumed total communications failure across scoring week (meters: %d)",
                            gf.f05MetersExposure());
            return new ScoredCandidate(gf, score, false, rationale);
        }

        // Component 1: Flagged offline hours (F01) normalized to 84h (half-week)
        double offlineNorm = Math.min(1.0, gf.f01FlaggedOfflineHours() / 84.0);

        // Component 2: Persistence (F03) normalized to 48h (critical sustained outage)
        double persistNorm = Math.min(1.0, gf.f03MaxConsecutiveOfflineHours() / 48.0);

        // Component 3: Reboot distress (F02) normalized to 24h
        double rebootNorm = Math.min(1.0, gf.f02FlaggedRebootHours() / 24.0);

        // Component 4: Trend trajectory factor (F04)
        double trendFactor = 1.0;
        if (gf.f04TrendTrajectoryRatio() > 1.2) {
            trendFactor = 1.2;
        } else if (gf.f04TrendTrajectoryRatio() < 0.5) {
            trendFactor = 0.8;
        }

        // Technical risk aggregation
        double techRisk =
                (0.45 * offlineNorm + 0.35 * persistNorm + 0.20 * rebootNorm) * trendFactor;

        // Fallback protection: if technical risk is negligible, do not allow meter count to elevate it
        if (techRisk < ACTIONABLE_RISK_THRESHOLD) {
            String rationale =
                    String.format(
                            "Low operational risk (tech risk: %.4f < %.2f); candidate included to fill visit budget",
                            techRisk, ACTIONABLE_RISK_THRESHOLD);
            return new ScoredCandidate(gf, techRisk, true, rationale);
        }

        // Business impact scaling
        double meterMultiplier = computeMeterMultiplier(gf.f05MetersExposure());
        double finalScore = techRisk * meterMultiplier * 100.0;

        String rationale = buildRationale(gf, techRisk, trendFactor);
        return new ScoredCandidate(gf, finalScore, false, rationale);
    }

    private static double computeMeterMultiplier(int meters) {
        int clampedMeters = Math.max(10, meters);
        return Math.log10(clampedMeters) / Math.log10(FLEET_MEDIAN_METERS);
    }

    private static String buildRationale(
            GatewayFeatures gf, double techRisk, double trendFactor) {
        StringBuilder sb = new StringBuilder();
        if (gf.f03MaxConsecutiveOfflineHours() >= 24) {
            sb.append(
                    String.format(
                            "Critical sustained outage: %dh consecutive offline",
                            gf.f03MaxConsecutiveOfflineHours()));
        } else if (gf.f02FlaggedRebootHours() >= 5) {
            sb.append(
                    String.format(
                            "Hardware/power distress: %dh reboot anomalies",
                            gf.f02FlaggedRebootHours()));
        } else {
            sb.append(
                    String.format(
                            "Elevated offline anomalies: %dh flagged offline",
                            gf.f01FlaggedOfflineHours()));
        }

        if (trendFactor > 1.0) {
            sb.append(String.format("; accelerating failure (trend %.2fx)", gf.f04TrendTrajectoryRatio()));
        } else if (trendFactor < 1.0) {
            sb.append(String.format("; recovering (trend %.2fx)", gf.f04TrendTrajectoryRatio()));
        }

        sb.append(String.format("; meters impacted: %d", gf.f05MetersExposure()));
        return sb.toString();
    }

    private static int compareCandidates(ScoredCandidate a, ScoredCandidate b) {
        // 1. Score descending
        if (Math.abs(b.score - a.score) > EPSILON) {
            return Double.compare(b.score, a.score);
        }
        // 2. F03 Max consecutive offline descending
        int cF03 =
                Integer.compare(
                        b.features.f03MaxConsecutiveOfflineHours(),
                        a.features.f03MaxConsecutiveOfflineHours());
        if (cF03 != 0) return cF03;

        // 3. F01 Flagged offline hours descending
        int cF01 =
                Integer.compare(
                        b.features.f01FlaggedOfflineHours(),
                        a.features.f01FlaggedOfflineHours());
        if (cF01 != 0) return cF01;

        // 4. F05 Meter exposure descending
        int cF05 =
                Integer.compare(
                        b.features.f05MetersExposure(),
                        a.features.f05MetersExposure());
        if (cF05 != 0) return cF05;

        // 5. GatewayId ascending (deterministic tie-breaker)
        return a.features.gatewayId().compareTo(b.features.gatewayId());
    }

    private record ScoredCandidate(
            GatewayFeatures features, double score, boolean isFallback, String rationale) {}
}
