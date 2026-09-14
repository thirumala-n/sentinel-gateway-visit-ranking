package com.lpdg.sentinel.application.model;

import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable canonical application result for a weekly prediction run.
 *
 * <p>Contains the full set of ranked gateway recommendations, their decision explanations,
 * and run metadata. Serves as the single source of truth for both REST API responses
 * and CSV file generation, guaranteeing zero divergence between formats.
 *
 * @param week target prediction week
 * @param items ordered list of prediction records (ranks 1..topN)
 * @param rankingMethod name of the ranking strategy applied
 * @param generatedAt UTC timestamp when this computation was executed
 */
public record WeeklyPredictionsResult(
        PredictionWeek week,
        List<PredictionRecord> items,
        String rankingMethod,
        Instant generatedAt) {

    public WeeklyPredictionsResult {
        Objects.requireNonNull(week, "week must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(rankingMethod, "rankingMethod must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        items = List.copyOf(items);
    }

    /**
     * Canonical record representing a single ranked recommendation within the weekly decision set.
     *
     * @param rank 1-based priority rank
     * @param gatewayId canonical gateway identifier
     * @param score numerical risk/priority score
     * @param reason compact explanation string (<= 300 chars, RFC-4180 safe)
     * @param explanation full structured decision evidence
     * @param fallback true if included under fallback policy to satisfy weekly visit capacity
     */
    public record PredictionRecord(
            int rank,
            GatewayId gatewayId,
            double score,
            String reason,
            DecisionExplanation explanation,
            boolean fallback) {

        public PredictionRecord {
            Objects.requireNonNull(gatewayId, "gatewayId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(explanation, "explanation must not be null");
        }
    }
}
