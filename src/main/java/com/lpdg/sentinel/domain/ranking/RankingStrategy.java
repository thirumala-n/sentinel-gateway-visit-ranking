package com.lpdg.sentinel.domain.ranking;

import com.lpdg.sentinel.domain.model.GatewayId;
import java.util.List;

/**
 * Strategy interface for ranking gateways by visit priority.
 *
 * <p>Implementations receive a {@link RankingContext} containing all precomputed feature
 * vectors and return an ordered list of {@link RankedGatewayDecision} records (highest priority first).
 *
 * <p>Implementations must NOT depend directly on repositories, DuckDB, CSV files, or any
 * infrastructure component. All necessary data is pre-loaded in the {@link RankingContext}.
 */
@FunctionalInterface
public interface RankingStrategy {

    /**
     * Ranks all eligible gateways in the context and returns at most {@code topN} visit
     * decisions ordered by descending priority.
     *
     * @param context precomputed feature context for the prediction week
     * @param topN maximum number of visit candidates to return (weekly budget, e.g. 15)
     * @return ordered list of visit decisions; highest priority first; at most {@code topN} entries
     */
    List<RankedGatewayDecision> rank(RankingContext context, int topN);

    /**
     * Convenience method returning only the ordered list of gateway IDs.
     *
     * @param context precomputed feature context for the prediction week
     * @param topN maximum number of visit candidates to return
     * @return ordered list of gateway IDs; at most {@code topN} entries
     */
    default List<GatewayId> rankGatewayIds(RankingContext context, int topN) {
        return rank(context, topN).stream().map(RankedGatewayDecision::gatewayId).toList();
    }
}
