package com.lpdg.sentinel.application.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.RankedGatewayDecision;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Determinism tests for {@link DecisionExplanationService}.
 *
 * <p>Verifies that all fields of a generated {@link DecisionExplanation} are identical
 * across repeated invocations for the same inputs, including csvReason, summary, and
 * all categorical fields.
 */
class ExplanationDeterminismTest {

    private static final PredictionWeek WEEK = new PredictionWeek(LocalDate.of(2026, 3, 9));
    // Valid 12-char uppercase hex GatewayIds
    private static final GatewayId GW   = GatewayId.of("AABBCC001122");
    private static final GatewayId GW_B = GatewayId.of("DDEEFF003344");
    private static final GatewayId GW_C = GatewayId.of("112233445566");

    private DecisionExplanationService service;
    private GatewayFeatures features;
    private RankedGatewayDecision decision;

    @BeforeEach
    void setUp() {
        service = new DecisionExplanationService();
        features = GatewayFeatures.builder(GW, WEEK)
                .f01FlaggedOfflineHours(25)
                .f02FlaggedRebootHours(8)
                .f03MaxConsecutiveOfflineHours(15)
                .f04TrendTrajectoryRatio(1.5)
                .f05MetersExposure(312)
                .build();
        decision = new RankedGatewayDecision(GW, 2, 72.3, false, "High risk gateway");
    }

    @RepeatedTest(5)
    void csvReason_isIdenticalAcrossRepeatedCalls() {
        String first = service.explain(features, decision).csvReason();
        String second = service.explain(features, decision).csvReason();
        assertThat(first).isEqualTo(second);
    }

    @RepeatedTest(5)
    void summary_isIdenticalAcrossRepeatedCalls() {
        String first = service.explain(features, decision).summary();
        String second = service.explain(features, decision).summary();
        assertThat(first).isEqualTo(second);
    }

    @RepeatedTest(5)
    void category_isIdenticalAcrossRepeatedCalls() {
        var first = service.explain(features, decision).category();
        var second = service.explain(features, decision).category();
        assertThat(first).isEqualTo(second);
    }

    @RepeatedTest(5)
    void riskLevel_isIdenticalAcrossRepeatedCalls() {
        var first = service.explain(features, decision).riskLevel();
        var second = service.explain(features, decision).riskLevel();
        assertThat(first).isEqualTo(second);
    }

    @RepeatedTest(5)
    void featureContributions_areIdenticalAcrossRepeatedCalls() {
        var first = service.explain(features, decision).featureContributions();
        var second = service.explain(features, decision).featureContributions();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void rankingComparisonReport_isDeterministic() {
        GatewayFeatures featuresB = GatewayFeatures.builder(GW_B, WEEK)
                .f01FlaggedOfflineHours(10)
                .f03MaxConsecutiveOfflineHours(5)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decisionB = new RankedGatewayDecision(GW_B, 5, 40.0, false, "Lower risk");

        DecisionExplanation expA = service.explain(features, decision);
        DecisionExplanation expB = service.explain(featuresB, decisionB);

        RankingComparisonReport report1 = RankingComparisonReport.compare(expA, expB);
        RankingComparisonReport report2 = RankingComparisonReport.compare(expA, expB);

        assertThat(report1.reasons()).isEqualTo(report2.reasons());
        assertThat(report1.scoreProximityWarning()).isEqualTo(report2.scoreProximityWarning());
        assertThat(report1.higherGateway()).isEqualTo(report2.higherGateway());
    }

    @Test
    void rankingComparisonReport_rejectsInvalidRankOrder() {
        GatewayFeatures featuresB = GatewayFeatures.builder(GW_B, WEEK)
                .f01FlaggedOfflineHours(10)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decisionB = new RankedGatewayDecision(GW_B, 5, 40.0, false, "Lower risk");

        DecisionExplanation expA = service.explain(features, decision);   // rank 2
        DecisionExplanation expB = service.explain(featuresB, decisionB); // rank 5

        // Passing lower-ranked as "higher" should throw
        assertThatThrownBy(() -> RankingComparisonReport.compare(expB, expA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rankingComparisonReport_scoreProximityWarning_flaggedWhenClose() {
        GatewayFeatures featuresB = GatewayFeatures.builder(GW_C, WEEK)
                .f01FlaggedOfflineHours(20)
                .f03MaxConsecutiveOfflineHours(10)
                .f05MetersExposure(300)
                .build();
        // Score A=72.3, B=70.0 → difference = 2.3 < 5.0 → proximity warning expected
        RankedGatewayDecision decisionB = new RankedGatewayDecision(GW_C, 3, 70.0, false, "Close rank");

        DecisionExplanation expA = service.explain(features, decision);
        DecisionExplanation expB = service.explain(featuresB, decisionB);

        RankingComparisonReport report = RankingComparisonReport.compare(expA, expB);

        assertThat(report.scoreProximityWarning()).isTrue();
        assertThat(report.reasons()).anyMatch(r ->
                r.toLowerCase().contains("close") || r.toLowerCase().contains("sensitiv"));
    }
}
