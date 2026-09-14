package com.lpdg.sentinel.application.explanation;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Adversarial tests for {@link DecisionExplanationService}.
 *
 * <p>Tests the "healthy high-meter" and "severe low-meter" boundary cases that are most likely
 * to produce incorrect explanations if the service does not separate R_tech computation
 * from meter scaling correctly.
 */
class ExplanationAdversarialTest {

    private static final PredictionWeek WEEK = new PredictionWeek(LocalDate.of(2026, 3, 9));

    // Valid 12-char uppercase hex gateway IDs
    private static final GatewayId GW_HEALTHY_HIGH = GatewayId.of("AABBCCDDEEFF");
    private static final GatewayId GW_SEVERE_LOW   = GatewayId.of("001122334455");
    private static final GatewayId GW_MISSING_TEL  = GatewayId.of("FFEEDDCCBBAA");
    private static final GatewayId GW_ACTIONABLE   = GatewayId.of("112233445566");
    private static final GatewayId GW_THRESHOLD    = GatewayId.of("AABBCC001122");

    private DecisionExplanationService service;

    @BeforeEach
    void setUp() {
        service = new DecisionExplanationService();
    }

    /**
     * Adversarial Case 1: Healthy high-meter gateway.
     *
     * <p>A gateway with zero offline anomalies and 800 installed meters must still be classified
     * as FALLBACK — it must NOT receive ACTIONABLE_RISK or HIGH_PRIORITY categorization.
     * The explanation must reflect that technical risk is below actionable threshold.
     */
    @Test
    void healthyHighMeter_mustBeClassifiedAsFallback_notActionableRisk() {
        GatewayFeatures features = GatewayFeatures.builder(GW_HEALTHY_HIGH, WEEK)
                .f01FlaggedOfflineHours(0)
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(0)
                .f04TrendTrajectoryRatio(1.0)
                .f05MetersExposure(800)
                .build();

        // The ranking engine would score this as fallback (R_tech = 0)
        RankedGatewayDecision decision = new RankedGatewayDecision(
                GW_HEALTHY_HIGH, 15, 0.0, true, "Fallback: low tech risk");

        DecisionExplanation exp = service.explain(features, decision);

        // Must be FALLBACK, NOT ACTIONABLE_RISK despite 800 meters
        assertThat(exp.category()).isEqualTo(DecisionCategory.FALLBACK);
        assertThat(exp.riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(exp.fallback()).isTrue();

        // csvReason must mention budget, not pretend there's a risk
        assertThat(exp.csvReason()).containsIgnoringCase("visit budget");
        assertThat(exp.csvReason()).doesNotContainIgnoringCase("outage");
        assertThat(exp.csvReason()).doesNotContainIgnoringCase("anomal");

        // All feature contributions should show NOMINAL for F01, F02, F03
        exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F01"))
                .forEach(fc -> assertThat(fc.severityLabel()).isEqualTo("NOMINAL"));
        exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F03"))
                .forEach(fc -> assertThat(fc.severityLabel()).isEqualTo("NOMINAL"));
    }

    /**
     * Adversarial Case 2: Severe low-meter gateway.
     *
     * <p>A gateway with a 36h continuous outage and only 30 meters must still be classified
     * as CRITICAL risk — it must not be suppressed by low meter count.
     */
    @Test
    void severeLowMeter_mustBeClassifiedAsCritical_notSuppressedByLowMeters() {
        GatewayFeatures features = GatewayFeatures.builder(GW_SEVERE_LOW, WEEK)
                .f01FlaggedOfflineHours(36)
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(36)
                .f04TrendTrajectoryRatio(1.5)
                .f05MetersExposure(30)
                .build();

        RankedGatewayDecision decision = new RankedGatewayDecision(
                GW_SEVERE_LOW, 8, 25.0, false, "Low meters but severe outage");

        DecisionExplanation exp = service.explain(features, decision);

        // Risk level must be CRITICAL because F03 >= 24h
        assertThat(exp.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(exp.category()).isNotEqualTo(DecisionCategory.FALLBACK);

        // F03 contribution must be labelled CRITICAL
        exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F03"))
                .forEach(fc -> assertThat(fc.severityLabel()).isEqualTo("CRITICAL"));

        // F05 must still show low meters exposure but active (because R_tech >= 0.05)
        exp.featureContributions().stream()
                .filter(fc -> fc.featureCode().equals("F05"))
                .forEach(fc -> {
                    assertThat(fc.valueFormatted()).contains("30");
                    assertThat(fc.active()).isTrue();
                });
    }

    /**
     * Adversarial Case 3: MISSING_TELEMETRY with zero feature values.
     *
     * <p>Missing telemetry produces all-zero feature values by default. The explanation must
     * still correctly reflect high-priority CRITICAL risk despite zero anomaly counts.
     */
    @Test
    void missingTelemetry_withZeroFeatures_mustProduceCriticalExplanation() {
        GatewayFeatures features = GatewayFeatures.builder(GW_MISSING_TEL, WEEK)
                .dataConfidence(DataConfidence.MISSING_TELEMETRY)
                .f01FlaggedOfflineHours(0) // all zero — default for missing telemetry
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(0)
                .f05MetersExposure(100)
                .build();

        RankedGatewayDecision decision = new RankedGatewayDecision(
                GW_MISSING_TEL, 2, 105.0, false, "Missing telemetry");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.MISSING_TELEMETRY);
        assertThat(exp.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(exp.warnings()).anyMatch(w -> w.toLowerCase().contains("zero")
                || w.toLowerCase().contains("no telemetry"));
        assertThat(exp.csvReason()).containsIgnoringCase("communications failure");
    }

    /**
     * Adversarial Case 4: Exact threshold boundary for ACTIONABLE_RISK (R_tech >= 0.50).
     *
     * <p>R_tech = (0.45 * 42/84 + 0.35 * 28/48 + 0) * 1.2 = (0.225 + 0.2042) * 1.2 = 0.5150
     */
    @Test
    void rTech_atOrAbove050_producesCategory_ACTIONABLE_RISK() {
        GatewayFeatures features = GatewayFeatures.builder(GW_ACTIONABLE, WEEK)
                .f01FlaggedOfflineHours(42)
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(28)
                .f04TrendTrajectoryRatio(1.5) // deteriorating → trendFactor = 1.2
                .f05MetersExposure(200)
                .build();

        double rTech = DecisionExplanationService.computeRTech(features);
        assertThat(rTech).isGreaterThanOrEqualTo(SeverityThresholds.TECH_RISK_CRITICAL);

        RankedGatewayDecision decision = new RankedGatewayDecision(
                GW_ACTIONABLE, 1, 80.0, false, "Actionable risk");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.category()).isEqualTo(DecisionCategory.ACTIONABLE_RISK);
        assertThat(exp.riskLevel()).isIn(RiskLevel.CRITICAL, RiskLevel.HIGH);
    }

    /**
     * Adversarial Case 5: R_tech just above actionable threshold (0.05).
     *
     * <p>F01=10h: R_tech = 0.45 * 10/84 ≈ 0.0536 — just above 0.05. Must NOT be fallback.
     */
    @Test
    void rTech_justAboveActionableThreshold_isNotFallback() {
        GatewayFeatures features = GatewayFeatures.builder(GW_THRESHOLD, WEEK)
                .f01FlaggedOfflineHours(10)
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(0)
                .f04TrendTrajectoryRatio(1.0)
                .f05MetersExposure(156)
                .build();

        double rTech = DecisionExplanationService.computeRTech(features);
        assertThat(rTech).isGreaterThan(SeverityThresholds.TECH_RISK_ACTIONABLE);

        RankedGatewayDecision decision = new RankedGatewayDecision(
                GW_THRESHOLD, 10, 30.0, false, "Just above threshold");

        DecisionExplanation exp = service.explain(features, decision);

        assertThat(exp.fallback()).isFalse();
        assertThat(exp.category()).isNotEqualTo(DecisionCategory.FALLBACK);
    }
}
