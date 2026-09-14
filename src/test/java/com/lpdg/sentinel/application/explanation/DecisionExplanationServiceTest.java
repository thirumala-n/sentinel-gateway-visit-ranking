package com.lpdg.sentinel.application.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.lpdg.sentinel.domain.explanation.DecisionCategory;
import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.explanation.RiskLevel;
import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.RankedGatewayDecision;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DecisionExplanationService}.
 *
 * <p>Covers all 17 required explanation paths per Phase 5 specification plus null validation.
 */
class DecisionExplanationServiceTest {

    private static final PredictionWeek WEEK = new PredictionWeek(LocalDate.of(2026, 3, 9));
    private static final GatewayId GW_A = GatewayId.of("AAAAAAAAAAAA");

    private DecisionExplanationService service;

    @BeforeEach
    void setUp() {
        service = new DecisionExplanationService();
    }

    // ── Test 1: MISSING_TELEMETRY category ──────────────────────────────────

    @Test
    void missingTelemetry_producesCategory_MISSING_TELEMETRY() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .dataConfidence(DataConfidence.MISSING_TELEMETRY)
                .f05MetersExposure(300)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 120.0, false, "Missing telemetry");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.MISSING_TELEMETRY);
        assertThat(exp.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(exp.fallback()).isFalse();
    }

    // ── Test 2: MISSING_TELEMETRY csvReason ────────────────────────────────

    @Test
    void missingTelemetry_csvReason_mentions_metersAndCommunicationFailure() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .dataConfidence(DataConfidence.MISSING_TELEMETRY)
                .f05MetersExposure(200)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 100.0, false, "Missing telemetry");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.csvReason()).contains("200 meters");
        assertThat(exp.csvReason()).containsIgnoringCase("communications failure");
        assertThat(exp.csvReason().length()).isLessThanOrEqualTo(300);
    }

    // ── Test 3: FALLBACK category ───────────────────────────────────────────

    @Test
    void lowRisk_fallback_producesCategory_FALLBACK() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(0)
                .f03MaxConsecutiveOfflineHours(0)
                .f05MetersExposure(100)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 14, 0.002, true, "Fallback");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.FALLBACK);
        assertThat(exp.fallback()).isTrue();
        assertThat(exp.riskLevel()).isEqualTo(RiskLevel.NONE);
    }

    // ── Test 4: FALLBACK csvReason ──────────────────────────────────────────

    @Test
    void fallback_csvReason_mentionsVisitBudget() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(0)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 15, 0.001, true, "Fallback");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.csvReason()).containsIgnoringCase("visit budget");
        assertThat(exp.csvReason().length()).isLessThanOrEqualTo(300);
    }

    // ── Test 5: LOW_CONFIDENCE category ────────────────────────────────────

    @Test
    void lowHistory_producesCategory_LOW_CONFIDENCE() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .dataConfidence(DataConfidence.LOW_HISTORY)
                .f01FlaggedOfflineHours(5)
                .f03MaxConsecutiveOfflineHours(2)
                .f05MetersExposure(100)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 3, 30.0, false, "Low history");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.LOW_CONFIDENCE);
        assertThat(exp.warnings()).anyMatch(w -> w.contains("28 days"));
    }

    // ── Test 6: ACTIONABLE_RISK category ───────────────────────────────────

    @Test
    void highTechRisk_above50_producesCategory_ACTIONABLE_RISK() {
        // R_tech = (0.45 * 60/84 + 0.35 * 30/48 + 0.20 * 0) * 1.2 = 0.7071 > 0.50
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(60)
                .f03MaxConsecutiveOfflineHours(30)
                .f04TrendTrajectoryRatio(2.0)
                .f05MetersExposure(200)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 85.0, false, "High risk");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.ACTIONABLE_RISK);
    }

    // ── Test 7: HIGH_PRIORITY category ─────────────────────────────────────

    @Test
    void moderateTechRisk_between05and50_producesCategory_HIGH_PRIORITY() {
        // R_tech = (0.45 * 10/84 + 0.35 * 5/48) * 1.0 ≈ 0.089 — in (0.05, 0.50)
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(10)
                .f03MaxConsecutiveOfflineHours(5)
                .f05MetersExposure(200)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 2, 50.0, false, "Medium risk");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.HIGH_PRIORITY);
    }

    // ── Test 8: RiskLevel CRITICAL from F03 ─────────────────────────────────

    @Test
    void f03_24h_or_more_producesRiskLevel_CRITICAL() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f03MaxConsecutiveOfflineHours(24)
                .f01FlaggedOfflineHours(24)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 70.0, false, "Sustained outage");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    // ── Test 9: RiskLevel HIGH from F01 ─────────────────────────────────────

    @Test
    void f01_20h_or_more_producesRiskLevel_at_least_HIGH() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(20)
                .f03MaxConsecutiveOfflineHours(3)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 2, 50.0, false, "High f01");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.riskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
    }

    // ── Test 10: Feature contributions all present ──────────────────────────

    @Test
    void featureContributions_containsAllFiveFeatures() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(15)
                .f02FlaggedRebootHours(5)
                .f03MaxConsecutiveOfflineHours(6)
                .f04TrendTrajectoryRatio(1.8)
                .f05MetersExposure(250)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 60.0, false, "All signals");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.featureContributions()).hasSize(5);
        assertThat(exp.featureContributions().stream().map(fc -> fc.featureCode()))
                .containsExactly("F01", "F02", "F03", "F04", "F05");
    }

    // ── Test 11: F03 CRITICAL severity label ────────────────────────────────

    @Test
    void f03_24h_produces_CRITICAL_severityLabel() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f03MaxConsecutiveOfflineHours(24)
                .f01FlaggedOfflineHours(24)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 70.0, false, "Sustained");

        DecisionExplanation exp = service.explain(features, decision);

        var f03 = exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F03"))
                .findFirst()
                .orElseThrow();
        assertThat(f03.severityLabel()).isEqualTo("CRITICAL");
        assertThat(f03.active()).isTrue();
    }

    // ── Test 12: F04 trend label — DETERIORATING ────────────────────────────

    @Test
    void f04_deteriorating_produces_DETERIORATING_label() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(10)
                .f04TrendTrajectoryRatio(2.0)
                .f05MetersExposure(200)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 55.0, false, "Trend");

        DecisionExplanation exp = service.explain(features, decision);

        var f04 = exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F04"))
                .findFirst()
                .orElseThrow();
        assertThat(f04.severityLabel()).isEqualTo("DETERIORATING");
        assertThat(f04.active()).isTrue();
    }

    // ── Test 13: F04 trend label — RECOVERING ───────────────────────────────

    @Test
    void f04_recovering_produces_RECOVERING_label() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(10)
                .f04TrendTrajectoryRatio(0.3)
                .f05MetersExposure(200)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 3, 40.0, false, "Recovering");

        DecisionExplanation exp = service.explain(features, decision);

        var f04 = exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F04"))
                .findFirst()
                .orElseThrow();
        assertThat(f04.severityLabel()).isEqualTo("RECOVERING");
    }

    // ── Test 14: csvReason length always ≤ 300 ──────────────────────────────

    @Test
    void csvReason_neverExceedsMaxLength() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(84)
                .f02FlaggedRebootHours(24)
                .f03MaxConsecutiveOfflineHours(48)
                .f04TrendTrajectoryRatio(5.0)
                .f05MetersExposure(999)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 99.0, false, "Max stress");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.csvReason().length()).isLessThanOrEqualTo(DecisionExplanation.MAX_CSV_REASON_LENGTH);
    }

    // ── Test 15: Warnings emitted for LOW_HISTORY ───────────────────────────

    @Test
    void lowHistory_warning_isPresent() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .dataConfidence(DataConfidence.LOW_HISTORY)
                .f01FlaggedOfflineHours(5)
                .f05MetersExposure(100)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 5, 20.0, false, "Low history");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.warnings()).isNotEmpty();
        assertThat(exp.warnings()).anyMatch(w -> w.toLowerCase().contains("28 days"));
    }

    // ── Test 16: Limitations always present ─────────────────────────────────

    @Test
    void limitations_alwaysPresent() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(10)
                .f05MetersExposure(150)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 2, 50.0, false, "Normal");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.limitations()).isNotEmpty();
    }

    // ── Test 17: Summary contains rank, score, and riskLevel ────────────────

    @Test
    void summary_containsRank_score_riskLevel() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(15)
                .f03MaxConsecutiveOfflineHours(6)
                .f05MetersExposure(200)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 4, 55.3, false, "Normal");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.summary()).contains("#4");
        assertThat(exp.summary()).contains("55.30");
        assertThat(exp.summary()).containsAnyOf("MODERATE", "HIGH", "LOW");
    }

    // ── Test 18: R_tech recomputation match ─────────────────────────────────

    @Test
    void rTechRecomputation_matchesRankingFormulaExactly() {
        // F01=42h, F02=0, F03=24h, F04=stable(1.0), trendFactor=1.0
        // R_tech = (0.45 * 42/84 + 0.35 * 24/48 + 0.20 * 0) * 1.0 = (0.225 + 0.175) = 0.40
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f01FlaggedOfflineHours(42)
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(24)
                .f04TrendTrajectoryRatio(1.0)
                .f05MetersExposure(156)
                .build();

        double rTech = DecisionExplanationService.computeRTech(features);

        assertThat(rTech).isCloseTo(0.40, within(1e-9));
    }

    // ── Test 19: Null argument rejection ────────────────────────────────────

    @Test
    void nullArguments_areRejected() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f05MetersExposure(100)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 1, 10.0, false, "test");

        assertThatThrownBy(() -> service.explain(null, decision))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.explain(features, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Test 20: Mismatched gatewayId rejected ──────────────────────────────

    @Test
    void mismatchedGatewayId_throwsIllegalArgument() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .f05MetersExposure(100)
                .build();
        GatewayId other = GatewayId.of("BBBBBBBBBBBB");
        RankedGatewayDecision decision = new RankedGatewayDecision(other, 1, 10.0, false, "test");

        assertThatThrownBy(() -> service.explain(features, decision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gatewayId");
    }

    // ── Test 21: Confidence reflected in explanation ─────────────────────────

    @Test
    void dataConfidence_reflectedInExplanation() {
        GatewayFeatures features = GatewayFeatures.builder(GW_A, WEEK)
                .dataConfidence(DataConfidence.LOW_HISTORY)
                .f01FlaggedOfflineHours(5)
                .f05MetersExposure(100)
                .build();
        RankedGatewayDecision decision = new RankedGatewayDecision(GW_A, 7, 20.0, false, "Low history");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.confidence()).isEqualTo(DataConfidence.LOW_HISTORY);
    }
}
