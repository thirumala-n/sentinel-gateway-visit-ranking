package com.lpdg.sentinel.domain.ranking;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable aggregate of all gateway feature vectors for a single prediction week.
 *
 * <p>This is the sole input passed from the application layer to the
 * {@link RankingStrategy}. It contains data, not repository references. The ranking
 * strategy is completely isolated from infrastructure concerns.
 *
 * <p>Populated by {@link com.lpdg.sentinel.application.service.FeatureExtractionService}
 * after all features have been computed for every eligible gateway.
 *
 * @param week the prediction week these features correspond to
 * @param gatewayFeatures immutable list of feature vectors, one per eligible gateway
 */
public record RankingContext(PredictionWeek week, List<GatewayFeatures> gatewayFeatures) {

    public RankingContext {
        Objects.requireNonNull(week, "week must not be null");
        Objects.requireNonNull(gatewayFeatures, "gatewayFeatures must not be null");
        // Defensive copy to preserve immutability
        gatewayFeatures = Collections.unmodifiableList(List.copyOf(gatewayFeatures));
    }

    /**
     * Returns the number of eligible gateways in this ranking context.
     *
     * @return size of the gateway feature list
     */
    public int size() {
        return gatewayFeatures.size();
    }
}
