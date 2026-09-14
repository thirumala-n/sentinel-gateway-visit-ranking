package com.lpdg.sentinel.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskBasedRankingStrategyTest {

    private static final PredictionWeek WEEK =
            new PredictionWeek(LocalDate.of(2026, 3, 9));

    private RiskBasedRankingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RiskBasedRankingStrategy();
    }

    @Test
    void persistenceOutweighsIsolatedFlaps() {
        // Case A: 15 isolated hours (maxConsecutive = 1)
        GatewayFeatures isolated =
                GatewayFeatures.builder(GatewayId.of("AAAAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(15)
                        .f03MaxConsecutiveOfflineHours(1)
                        .f05MetersExposure(156)
                        .build();

        // Case B: 15 consecutive hours (maxConsecutive = 15)
        GatewayFeatures consecutive =
                GatewayFeatures.builder(GatewayId.of("BBBBBBBBBBBB"), WEEK)
                        .f01FlaggedOfflineHours(15)
                        .f03MaxConsecutiveOfflineHours(15)
                        .f05MetersExposure(156)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(isolated, consecutive));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("BBBBBBBBBBBB"));
        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("AAAAAAAAAAAA"));
        assertThat(decisions.get(0).score()).isGreaterThan(decisions.get(1).score());
    }

    @Test
    void deterioratingTrajectoryOutweighsRecoveringTrajectory() {
        // Case A: Deteriorating (trend = 2.5)
        GatewayFeatures deteriorating =
                GatewayFeatures.builder(GatewayId.of("AAAAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(20)
                        .f03MaxConsecutiveOfflineHours(5)
                        .f04TrendTrajectoryRatio(2.5)
                        .f05MetersExposure(156)
                        .build();

        // Case B: Recovering (trend = 0.2)
        GatewayFeatures recovering =
                GatewayFeatures.builder(GatewayId.of("BBBBBBBBBBBB"), WEEK)
                        .f01FlaggedOfflineHours(20)
                        .f03MaxConsecutiveOfflineHours(5)
                        .f04TrendTrajectoryRatio(0.2)
                        .f05MetersExposure(156)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(recovering, deteriorating));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("AAAAAAAAAAAA"));
        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("BBBBBBBBBBBB"));
        assertThat(decisions.get(0).score()).isGreaterThan(decisions.get(1).score());
    }

    @Test
    void healthyHighMeterGatewayDoesNotOvertakeFailingLowMeterGateway() {
        // Healthy gateway with massive customer meter count (800 meters)
        GatewayFeatures healthyHighMeter =
                GatewayFeatures.builder(GatewayId.of("AAAAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(0)
                        .f03MaxConsecutiveOfflineHours(0)
                        .f05MetersExposure(800)
                        .build();

        // Failing gateway with small customer meter count (50 meters)
        GatewayFeatures failingLowMeter =
                GatewayFeatures.builder(GatewayId.of("BBBBBBBBBBBB"), WEEK)
                        .f01FlaggedOfflineHours(30)
                        .f03MaxConsecutiveOfflineHours(12)
                        .f05MetersExposure(50)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(healthyHighMeter, failingLowMeter));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("BBBBBBBBBBBB"));
        assertThat(decisions.get(0).fallback()).isFalse();

        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("AAAAAAAAAAAA"));
        assertThat(decisions.get(1).fallback()).isTrue();
    }

    @Test
    void highRiskHighMeterOutranksHighRiskLowMeter() {
        GatewayFeatures highRiskLowMeter =
                GatewayFeatures.builder(GatewayId.of("AAAAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(40)
                        .f03MaxConsecutiveOfflineHours(20)
                        .f05MetersExposure(50)
                        .build();

        GatewayFeatures highRiskHighMeter =
                GatewayFeatures.builder(GatewayId.of("BBBBBBBBBBBB"), WEEK)
                        .f01FlaggedOfflineHours(40)
                        .f03MaxConsecutiveOfflineHours(20)
                        .f05MetersExposure(500)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(highRiskLowMeter, highRiskHighMeter));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("BBBBBBBBBBBB"));
        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("AAAAAAAAAAAA"));
        assertThat(decisions.get(0).score()).isGreaterThan(decisions.get(1).score());
    }

    @Test
    void missingTelemetryPrioritizedAsPresumedDead() {
        GatewayFeatures missing =
                GatewayFeatures.builder(GatewayId.of("AAAAAAAAAAAA"), WEEK)
                        .dataConfidence(DataConfidence.MISSING_TELEMETRY)
                        .f05MetersExposure(200)
                        .build();

        GatewayFeatures moderateAnomaly =
                GatewayFeatures.builder(GatewayId.of("BBBBBBBBBBBB"), WEEK)
                        .f01FlaggedOfflineHours(10)
                        .f03MaxConsecutiveOfflineHours(2)
                        .f05MetersExposure(200)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(moderateAnomaly, missing));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("AAAAAAAAAAAA"));
        // Verify exact missing-telemetry score formula: 100.0 * log10(meters) / log10(FLEET_MEDIAN)
        double expectedScore = 100.0 * Math.log10(200.0) / Math.log10(RiskBasedRankingStrategy.FLEET_MEDIAN_METERS);
        assertThat(decisions.get(0).score()).isCloseTo(expectedScore, within(0.001));
        assertThat(decisions.get(0).rationale()).contains("Missing telemetry");
    }

    @Test
    void respectsTopNBudgetCap() {
        List<GatewayFeatures> list = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            list.add(
                    GatewayFeatures.builder(GatewayId.of(String.format("%012X", i + 1)), WEEK)
                            .f01FlaggedOfflineHours(i + 1)
                            .f03MaxConsecutiveOfflineHours(i + 1)
                            .build());
        }

        RankingContext ctx = new RankingContext(WEEK, list);
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions).hasSize(15);
        assertThat(decisions.get(0).rank()).isEqualTo(1);
        assertThat(decisions.get(14).rank()).isEqualTo(15);
    }

    @Test
    void deterministicTieBreakingHierarchy() {
        // Equal scores (identical features except ID)
        GatewayFeatures gf1 =
                GatewayFeatures.builder(GatewayId.of("0EAAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(20)
                        .f03MaxConsecutiveOfflineHours(5)
                        .f05MetersExposure(156)
                        .build();
        GatewayFeatures gf2 =
                GatewayFeatures.builder(GatewayId.of("02AAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(20)
                        .f03MaxConsecutiveOfflineHours(5)
                        .f05MetersExposure(156)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(gf1, gf2));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions).hasSize(2);
        // "02..." comes first lexicographically
        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("02AAAAAAAAAA"));
        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("0EAAAAAAAAAA"));
    }

    @Test
    void rejectionOfInvalidArguments() {
        RankingContext ctx = new RankingContext(WEEK, List.of());
        assertThatThrownBy(() -> strategy.rank(null, 15))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> strategy.rank(ctx, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
