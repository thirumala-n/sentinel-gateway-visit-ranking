package com.lpdg.sentinel.domain.ranking;

import com.lpdg.sentinel.domain.model.GatewayId;
import java.util.Objects;

/**
 * Immutable decision record representing a prioritized gateway visit recommendation.
 *
 * <p>Contains the target gateway identity, ordinal ranking (1-based), calibrated priority
 * score, fallback status flag, and human-readable operational rationale for dispatchers.
 *
 * @param gatewayId canonical identifier of the recommended gateway
 * @param rank 1-based position in the weekly visit schedule (1 = highest priority)
 * @param score calibrated priority score (higher indicates greater urgency/impact)
 * @param fallback {@code true} if this candidate was included to satisfy the visit budget
 *     despite insufficient evidence of active failure (technical risk < 0.05)
 * @param rationale concise operational justification explaining why this visit is recommended
 */
public record RankedGatewayDecision(
        GatewayId gatewayId, int rank, double score, boolean fallback, String rationale) {

    public RankedGatewayDecision {
        Objects.requireNonNull(gatewayId, "gatewayId must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got: " + rank);
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score must be a finite number, got: " + score);
        }
    }
}
