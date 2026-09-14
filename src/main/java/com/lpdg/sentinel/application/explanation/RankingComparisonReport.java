package com.lpdg.sentinel.application.explanation;

import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.model.GatewayId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pairwise comparison report explaining why one gateway was ranked above another.
 *
 * <p>All reasons are feature-referenced and grounded in actual feature values.
 * No score-value comparison alone is presented without the underlying feature evidence.
 *
 * @param higherGateway gateway ranked higher (lower rank number)
 * @param lowerGateway gateway ranked lower (higher rank number)
 * @param reasons ordered list of factual, feature-referenced justifications
 * @param scoreProximityWarning {@code true} if score difference is &lt; 5.0, indicating
 *     rank sensitivity to minor telemetry variations
 */
public record RankingComparisonReport(
        GatewayId higherGateway,
        GatewayId lowerGateway,
        List<String> reasons,
        boolean scoreProximityWarning) {

    public RankingComparisonReport {
        Objects.requireNonNull(higherGateway, "higherGateway must not be null");
        Objects.requireNonNull(lowerGateway, "lowerGateway must not be null");
        reasons = List.copyOf(reasons);
    }

    /**
     * Produces a pairwise comparison report explaining why {@code higher} outranks {@code lower}.
     *
     * @param higher explanation for the higher-ranked gateway
     * @param lower explanation for the lower-ranked gateway
     * @return comparison report with feature-referenced reasons
     * @throws IllegalArgumentException if higher.rank >= lower.rank
     */
    public static RankingComparisonReport compare(DecisionExplanation higher, DecisionExplanation lower) {
        Objects.requireNonNull(higher, "higher must not be null");
        Objects.requireNonNull(lower, "lower must not be null");
        if (higher.rank() >= lower.rank()) {
            throw new IllegalArgumentException(
                    "higher.rank (" + higher.rank() + ") must be less than lower.rank (" + lower.rank() + ")");
        }

        List<String> reasons = buildReasons(higher, lower);
        boolean proximityWarning = Math.abs(higher.score() - lower.score())
                < SeverityThresholds.SCORE_PROXIMITY_THRESHOLD;

        if (proximityWarning) {
            reasons = new ArrayList<>(reasons);
            reasons.add(String.format(
                    "Scores are close (A: %.2f, B: %.2f); ordering may be sensitive to minor telemetry changes.",
                    higher.score(), lower.score()));
            reasons = List.copyOf(reasons);
        }

        return new RankingComparisonReport(higher.gatewayId(), lower.gatewayId(), reasons, proximityWarning);
    }

    private static List<String> buildReasons(DecisionExplanation higher, DecisionExplanation lower) {
        List<String> reasons = new ArrayList<>();

        // Category difference is the most decisive signal
        if (higher.category() != lower.category()) {
            reasons.add(String.format(
                    "Gateway %s is categorized %s vs Gateway %s categorized %s.",
                    higher.gatewayId(), higher.category(),
                    lower.gatewayId(), lower.category()));
        }

        // Feature-level comparisons from contributions
        var higherMap = featureMap(higher);
        var lowerMap = featureMap(lower);

        // F03 comparison (continuous outage — most severe signal)
        Integer h03 = higherMap.get("F03_raw");
        Integer l03 = lowerMap.get("F03_raw");
        if (h03 != null && l03 != null && h03 > l03) {
            reasons.add(String.format(
                    "Gateway %s has a %dh continuous outage vs Gateway %s's %dh (F03: %d > %d).",
                    higher.gatewayId(), h03, lower.gatewayId(), l03, h03, l03));
        }

        // F01 comparison
        Integer h01 = higherMap.get("F01_raw");
        Integer l01 = lowerMap.get("F01_raw");
        if (h01 != null && l01 != null && h01 > l01) {
            reasons.add(String.format(
                    "Gateway %s has more offline anomaly hours (%dh vs %dh, F01).",
                    higher.gatewayId(), h01, l01));
        }

        // Score comparison always as context
        reasons.add(String.format(
                "Score: %s=%.2f vs %s=%.2f.",
                higher.gatewayId(), higher.score(),
                lower.gatewayId(), lower.score()));

        return reasons;
    }

    /** Extracts raw integer feature values from formatted contribution strings. */
    private static java.util.Map<String, Integer> featureMap(DecisionExplanation explanation) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (var fc : explanation.featureContributions()) {
            try {
                // Parse "42h" → 42, "186 meters" → 186
                String trimmed = fc.valueFormatted().trim();
                String digits = trimmed.split("[^0-9]")[0];
                if (!digits.isEmpty()) {
                    map.put(fc.featureCode() + "_raw", Integer.parseInt(digits));
                }
            } catch (NumberFormatException ignored) {
                // Non-integer features (F04 trend) are skipped in direct comparison
            }
        }
        return map;
    }
}
