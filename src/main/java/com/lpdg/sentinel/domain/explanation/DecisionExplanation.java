package com.lpdg.sentinel.domain.explanation;

import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.model.GatewayId;
import java.util.List;
import java.util.Objects;

/**
 * Complete, traceable decision evidence record for a prioritized gateway visit.
 *
 * <p>Every field is directly grounded in observable telemetry features, documented ranking
 * policies, or data confidence states. No ungrounded natural language generation is performed.
 *
 * @param rank 1-based priority position in the weekly visit recommendation list
 * @param gatewayId canonical identifier of the gateway
 * @param score calibrated priority score from the ranking engine
 * @param fallback {@code true} if candidate was selected solely to satisfy visit capacity
 * @param category operational decision classification
 * @param riskLevel calibrated technical risk severity
 * @param featureContributions itemized feature values and contributions
 * @param confidence data confidence level associated with this gateway's history
 * @param warnings data quality and telemetry reliability caveats
 * @param limitations known domain boundaries and sensing constraints
 * @param csvReason concise operational reason (&le; 300 characters) suitable for CSV exports
 * @param summary comprehensive human-readable explanation for field dispatchers
 */
public record DecisionExplanation(
        int rank,
        GatewayId gatewayId,
        double score,
        boolean fallback,
        DecisionCategory category,
        RiskLevel riskLevel,
        List<FeatureContribution> featureContributions,
        DataConfidence confidence,
        List<String> warnings,
        List<String> limitations,
        String csvReason,
        String summary) {

    public static final int MAX_CSV_REASON_LENGTH = 300;

    public DecisionExplanation {
        Objects.requireNonNull(gatewayId, "gatewayId must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(csvReason, "csvReason must not be null");
        Objects.requireNonNull(summary, "summary must not be null");

        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got: " + rank);
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score must be a finite number, got: " + score);
        }
        if (csvReason.length() > MAX_CSV_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "csvReason exceeds maximum length of " + MAX_CSV_REASON_LENGTH + " characters: " + csvReason.length());
        }

        featureContributions = List.copyOf(featureContributions);
        warnings = List.copyOf(warnings);
        limitations = List.copyOf(limitations);
    }
}
