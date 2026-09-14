package com.lpdg.sentinel.application.service;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.application.explanation.RankingComparisonReport;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult.PredictionRecord;
import com.lpdg.sentinel.common.errors.GatewayNotFoundException;
import com.lpdg.sentinel.common.errors.InvalidComparisonException;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.BaselineRankingStrategy;
import com.lpdg.sentinel.domain.ranking.RankedGatewayDecision;
import com.lpdg.sentinel.domain.ranking.RankingContext;
import com.lpdg.sentinel.domain.ranking.RankingStrategy;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core application service orchestrating prediction calculation, caching, explanation,
 * and pairwise comparison.
 *
 * <p>Serves as the boundary between REST controllers and domain engines. Implements thread-safe
 * in-memory caching to guarantee responsive API interactions (&lt;1ms for cached lookups), while
 * supporting explicit {@link #rerun(PredictionWeek)} to force full recomputations from source data.
 */
@Service
public class GatewayPredictionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(GatewayPredictionApplicationService.class);

    private final FeatureExtractionService featureExtractionService;
    private final RankingStrategy rankingStrategy;
    private final DecisionExplanationService explanationService;
    private final SentinelProperties properties;

    private final Map<PredictionWeek, WeeklyPredictionsResult> cache = new ConcurrentHashMap<>();

    public GatewayPredictionApplicationService(
            FeatureExtractionService featureExtractionService,
            RankingStrategy rankingStrategy,
            DecisionExplanationService explanationService,
            SentinelProperties properties) {
        this.featureExtractionService = Objects.requireNonNull(featureExtractionService, "featureExtractionService must not be null");
        this.rankingStrategy = Objects.requireNonNull(rankingStrategy, "rankingStrategy must not be null");
        this.explanationService = Objects.requireNonNull(explanationService, "explanationService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Retrieves the 15 ranked predictions for the given week, computing and caching if not present.
     *
     * @param week target prediction week
     * @return canonical weekly predictions result; never null
     */
    public WeeklyPredictionsResult getPredictions(PredictionWeek week) {
        Objects.requireNonNull(week, "week must not be null");
        return cache.computeIfAbsent(week, this::computePredictions);
    }

    /**
     * Forces an explicit recomputation of the ranking for the specified week, bypassing and updating cache.
     *
     * @param week target prediction week
     * @return freshly computed weekly predictions result; never null
     */
    public WeeklyPredictionsResult rerun(PredictionWeek week) {
        Objects.requireNonNull(week, "week must not be null");
        log.info("Explicit rerun requested for week {}. Evicting cache and recalculating from source data.", week.targetMonday());
        cache.remove(week);
        return getPredictions(week);
    }

    /**
     * Retrieves the structured decision explanation for a specific gateway in the weekly decision set.
     *
     * @param week target prediction week
     * @param gatewayId canonical gateway ID
     * @return structured decision explanation
     * @throws GatewayNotFoundException if the gateway is not in the weekly recommendations
     */
    public DecisionExplanation getExplanation(PredictionWeek week, GatewayId gatewayId) {
        Objects.requireNonNull(week, "week must not be null");
        Objects.requireNonNull(gatewayId, "gatewayId must not be null");

        WeeklyPredictionsResult predictions = getPredictions(week);
        return predictions.items().stream()
                .filter(record -> record.gatewayId().equals(gatewayId))
                .findFirst()
                .map(PredictionRecord::explanation)
                .orElseThrow(() -> new GatewayNotFoundException(
                        String.format("Gateway %s is not in the top %d recommendations for week %s",
                                gatewayId, properties.visitsPerWeek(), week.targetMonday())));
    }

    /**
     * Generates a pairwise comparison report explaining why one gateway outranked another in the decision set.
     *
     * @param week target prediction week
     * @param gatewayA first gateway
     * @param gatewayB second gateway
     * @return pairwise ranking comparison report
     * @throws InvalidComparisonException if attempting to compare a gateway to itself
     * @throws GatewayNotFoundException if either gateway is not in the weekly recommendations
     */
    public RankingComparisonReport compare(PredictionWeek week, GatewayId gatewayA, GatewayId gatewayB) {
        Objects.requireNonNull(week, "week must not be null");
        Objects.requireNonNull(gatewayA, "gatewayA must not be null");
        Objects.requireNonNull(gatewayB, "gatewayB must not be null");

        if (gatewayA.equals(gatewayB)) {
            throw new InvalidComparisonException("Cannot compare gateway to itself: " + gatewayA);
        }

        WeeklyPredictionsResult predictions = getPredictions(week);

        PredictionRecord recordA = predictions.items().stream()
                .filter(r -> r.gatewayId().equals(gatewayA))
                .findFirst()
                .orElseThrow(() -> new GatewayNotFoundException(
                        String.format("Gateway %s is not in the top %d recommendations for week %s",
                                gatewayA, properties.visitsPerWeek(), week.targetMonday())));

        PredictionRecord recordB = predictions.items().stream()
                .filter(r -> r.gatewayId().equals(gatewayB))
                .findFirst()
                .orElseThrow(() -> new GatewayNotFoundException(
                        String.format("Gateway %s is not in the top %d recommendations for week %s",
                                gatewayB, properties.visitsPerWeek(), week.targetMonday())));

        PredictionRecord higherRecord = recordA.rank() < recordB.rank() ? recordA : recordB;
        PredictionRecord lowerRecord = recordA.rank() < recordB.rank() ? recordB : recordA;

        return RankingComparisonReport.compare(higherRecord.explanation(), lowerRecord.explanation());
    }

    /**
     * Clears all cached weekly predictions (used primarily for test isolation).
     */
    public void clearCache() {
        cache.clear();
    }

    // ── Internal Computation Pipeline ──────────────────────────

    private WeeklyPredictionsResult computePredictions(PredictionWeek week) {
        long startMs = System.currentTimeMillis();
        log.info("Computing predictions for week {}...", week.targetMonday());

        RankingContext context = featureExtractionService.extractFeatures(week);
        int topN = properties.visitsPerWeek();
        List<RankedGatewayDecision> decisions = rankingStrategy.rank(context, topN);

        Map<GatewayId, GatewayFeatures> featuresByGateway = context.gatewayFeatures().stream()
                .collect(Collectors.toMap(GatewayFeatures::gatewayId, f -> f));

        List<PredictionRecord> records = new ArrayList<>(decisions.size());
        for (RankedGatewayDecision decision : decisions) {
            GatewayFeatures features = featuresByGateway.get(decision.gatewayId());
            if (features == null) {
                throw new IllegalStateException("Missing extracted features for ranked gateway: " + decision.gatewayId());
            }
            DecisionExplanation explanation = explanationService.explain(features, decision);
            records.add(new PredictionRecord(
                    decision.rank(),
                    decision.gatewayId(),
                    decision.score(),
                    explanation.csvReason(),
                    explanation,
                    decision.fallback()));
        }

        String strategyName = resolveStrategyName(rankingStrategy);
        Instant generatedAt = Instant.now();

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Computed {} predictions for week {} using {} in {}ms.",
                records.size(), week.targetMonday(), strategyName, durationMs);

        return new WeeklyPredictionsResult(week, records, strategyName, generatedAt);
    }

    private static String resolveStrategyName(RankingStrategy strategy) {
        if (strategy instanceof RiskBasedRankingStrategy) {
            return "RISK_BASED";
        }
        if (strategy instanceof BaselineRankingStrategy) {
            return "BASELINE";
        }
        return strategy.getClass().getSimpleName().replace("RankingStrategy", "").toUpperCase(Locale.ROOT);
    }
}
