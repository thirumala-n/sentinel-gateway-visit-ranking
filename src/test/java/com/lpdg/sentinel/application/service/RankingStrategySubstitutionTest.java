package com.lpdg.sentinel.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.BaselineRankingStrategy;
import com.lpdg.sentinel.domain.ranking.RankingStrategy;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import com.lpdg.sentinel.infrastructure.configuration.DuckDbConfiguration;
import com.lpdg.sentinel.infrastructure.csv.CsvMeterReadRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbGatewayRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbTelemetryRepository;
import java.io.File;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validates Section 12 requirement:
 * Live-demo strategy substitution readiness.
 *
 * <p>Proves that swapping {@link BaselineRankingStrategy} and {@link RiskBasedRankingStrategy}
 * preserves the entire contract (15 decisions, unique gateway IDs, ranks 1..15, valid reasons)
 * without requiring application service or controller changes.
 */
class RankingStrategySubstitutionTest {

    private FeatureExtractionService featureService;
    private DecisionExplanationService explanationService;
    private SentinelProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        File dataDir = new File("data");
        Assumptions.assumeTrue(
                dataDir.exists() && new File(dataDir, "telemetry").exists(),
                "Skipping test: real data directory not present");

        properties = new SentinelProperties("data", 15, 28, 7, 4, "risk-based");
        DuckDbConfiguration duckDbConfig = new DuckDbConfiguration();
        DataSource dataSource = duckDbConfig.duckDbDataSource();

        DuckDbGatewayRepository gatewayRepo = new DuckDbGatewayRepository(dataSource, properties);
        DuckDbTelemetryRepository telemetryRepo = new DuckDbTelemetryRepository(dataSource, properties);
        CsvMeterReadRepository meterReadRepo = new CsvMeterReadRepository(properties);

        featureService = new FeatureExtractionService(gatewayRepo, telemetryRepo, meterReadRepo);
        explanationService = new DecisionExplanationService();
    }

    @Test
    void bothStrategies_satisfyIdenticalApplicationAndApiContracts() {
        PredictionWeek week = new PredictionWeek(LocalDate.of(2026, 3, 9));

        RankingStrategy baseline = new BaselineRankingStrategy();
        RankingStrategy riskBased = new RiskBasedRankingStrategy();

        GatewayPredictionApplicationService baselineService =
                new GatewayPredictionApplicationService(featureService, baseline, explanationService, properties);

        GatewayPredictionApplicationService riskService =
                new GatewayPredictionApplicationService(featureService, riskBased, explanationService, properties);

        WeeklyPredictionsResult baselineResult = baselineService.getPredictions(week);
        WeeklyPredictionsResult riskResult = riskService.getPredictions(week);

        // Contract Invariant 1: Exactly 15 decisions
        assertThat(baselineResult.items()).hasSize(15);
        assertThat(riskResult.items()).hasSize(15);

        // Contract Invariant 2: Correct strategy name reported
        assertThat(baselineResult.rankingMethod()).isEqualTo("BASELINE");
        assertThat(riskResult.rankingMethod()).isEqualTo("RISK_BASED");

        // Contract Invariant 3: Continuous ranks 1..15
        for (int i = 0; i < 15; i++) {
            assertThat(baselineResult.items().get(i).rank()).isEqualTo(i + 1);
            assertThat(riskResult.items().get(i).rank()).isEqualTo(i + 1);
        }

        // Contract Invariant 4: Distinct gateway IDs
        long distinctBaselineGateways = baselineResult.items().stream()
                .map(WeeklyPredictionsResult.PredictionRecord::gatewayId).distinct().count();
        long distinctRiskGateways = riskResult.items().stream()
                .map(WeeklyPredictionsResult.PredictionRecord::gatewayId).distinct().count();

        assertThat(distinctBaselineGateways).isEqualTo(15);
        assertThat(distinctRiskGateways).isEqualTo(15);

        // Contract Invariant 5: Valid, non-empty reasons <= 300 characters
        for (var item : baselineResult.items()) {
            assertThat(item.reason()).isNotBlank().hasSizeLessThanOrEqualTo(300);
            assertThat(Double.isNaN(item.score())).isFalse();
        }
        for (var item : riskResult.items()) {
            assertThat(item.reason()).isNotBlank().hasSizeLessThanOrEqualTo(300);
            assertThat(Double.isNaN(item.score())).isFalse();
        }
    }
}
