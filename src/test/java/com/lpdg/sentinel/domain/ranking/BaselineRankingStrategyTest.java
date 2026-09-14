package com.lpdg.sentinel.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaselineRankingStrategyTest {

    private static final PredictionWeek WEEK =
            new PredictionWeek(LocalDate.of(2026, 3, 9));

    private BaselineRankingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BaselineRankingStrategy();
    }

    @Test
    void ranksDescendingByTotal3SigmaFlags() {
        GatewayFeatures gf1 =
                GatewayFeatures.builder(GatewayId.of("AAAAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(10)
                        .f02FlaggedRebootHours(5) // Total: 15
                        .build();
        GatewayFeatures gf2 =
                GatewayFeatures.builder(GatewayId.of("BBBBBBBBBBBB"), WEEK)
                        .f01FlaggedOfflineHours(25)
                        .f02FlaggedRebootHours(10) // Total: 35
                        .build();
        GatewayFeatures gf3 =
                GatewayFeatures.builder(GatewayId.of("CCCCCCCCCCCC"), WEEK)
                        .f01FlaggedOfflineHours(5)
                        .f02FlaggedRebootHours(0) // Total: 5
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(gf1, gf2, gf3));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions).hasSize(3);
        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("BBBBBBBBBBBB"));
        assertThat(decisions.get(0).rank()).isEqualTo(1);
        assertThat(decisions.get(0).score()).isEqualTo(35.0);
        assertThat(decisions.get(0).fallback()).isFalse();

        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("AAAAAAAAAAAA"));
        assertThat(decisions.get(1).rank()).isEqualTo(2);
        assertThat(decisions.get(1).score()).isEqualTo(15.0);

        assertThat(decisions.get(2).gatewayId()).isEqualTo(GatewayId.of("CCCCCCCCCCCC"));
        assertThat(decisions.get(2).rank()).isEqualTo(3);
        assertThat(decisions.get(2).score()).isEqualTo(5.0);
    }

    @Test
    void respectsTopNLimit() {
        List<GatewayFeatures> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String hex = String.format("%012X", i + 1);
            list.add(
                    GatewayFeatures.builder(GatewayId.of(hex), WEEK)
                            .f01FlaggedOfflineHours(i + 1)
                            .build());
        }

        RankingContext ctx = new RankingContext(WEEK, list);
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions).hasSize(15);
        assertThat(decisions.get(0).rank()).isEqualTo(1);
        assertThat(decisions.get(14).rank()).isEqualTo(15);
        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of(String.format("%012X", 20)));
    }

    @Test
    void deterministicTieBreakingByGatewayIdAscending() {
        // Both have total 10 flags
        GatewayFeatures gfA =
                GatewayFeatures.builder(GatewayId.of("06AAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(10)
                        .build();
        GatewayFeatures gfB =
                GatewayFeatures.builder(GatewayId.of("02AAAAAAAAAA"), WEEK)
                        .f01FlaggedOfflineHours(10)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(gfA, gfB));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions).hasSize(2);
        // "02..." comes before "06..." lexicographically
        assertThat(decisions.get(0).gatewayId()).isEqualTo(GatewayId.of("02AAAAAAAAAA"));
        assertThat(decisions.get(1).gatewayId()).isEqualTo(GatewayId.of("06AAAAAAAAAA"));
    }

    @Test
    void zeroFlagsMarkedAsFallback() {
        GatewayFeatures gf =
                GatewayFeatures.builder(GatewayId.of("0123456789AB"), WEEK)
                        .f01FlaggedOfflineHours(0)
                        .f02FlaggedRebootHours(0)
                        .build();

        RankingContext ctx = new RankingContext(WEEK, List.of(gf));
        List<RankedGatewayDecision> decisions = strategy.rank(ctx, 15);

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).fallback()).isTrue();
        assertThat(decisions.get(0).score()).isEqualTo(0.0);
    }

    @Test
    void rejectsInvalidArguments() {
        RankingContext ctx = new RankingContext(WEEK, List.of());
        assertThatThrownBy(() -> strategy.rank(null, 15))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> strategy.rank(ctx, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.rank(ctx, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
