package com.lpdg.sentinel.domain.ranking;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reference baseline ranking strategy reproducing the competition 3-sigma anomaly count approach.
 *
 * <p>Scoring Formula:
 *
 * <pre>
 *   Score_baseline(g) = F01(g) + F02(g)
 * </pre>
 *
 * <p>Gateways are ordered by total anomaly flags descending, with {@link
 * com.lpdg.sentinel.domain.model.GatewayId} ascending as the deterministic tie-breaker.
 *
 * <p>This strategy serves as the control benchmark for historical validation and must
 * not be modified to make alternative strategies appear superior.
 */
public class BaselineRankingStrategy implements RankingStrategy {

    private static final Comparator<GatewayFeatures> BASELINE_COMPARATOR =
            Comparator.comparingInt(
                            (GatewayFeatures gf) -> gf.f01FlaggedOfflineHours() + gf.f02FlaggedRebootHours())
                    .reversed()
                    .thenComparing(gf -> gf.gatewayId().value());

    @Override
    public List<RankedGatewayDecision> rank(RankingContext context, int topN) {
        Objects.requireNonNull(context, "context must not be null");
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be >= 1, got: " + topN);
        }

        List<GatewayFeatures> sorted = new ArrayList<>(context.gatewayFeatures());
        sorted.sort(BASELINE_COMPARATOR);

        int limit = Math.min(topN, sorted.size());
        List<RankedGatewayDecision> results = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            GatewayFeatures gf = sorted.get(i);
            int totalFlags = gf.f01FlaggedOfflineHours() + gf.f02FlaggedRebootHours();
            boolean isFallback = totalFlags == 0;
            String rationale =
                    String.format(
                            "Baseline 3-sigma flags: %d (offline: %d, reboots: %d)",
                            totalFlags, gf.f01FlaggedOfflineHours(), gf.f02FlaggedRebootHours());

            results.add(
                    new RankedGatewayDecision(
                            gf.gatewayId(), i + 1, (double) totalFlags, isFallback, rationale));
        }

        return List.copyOf(results);
    }
}
