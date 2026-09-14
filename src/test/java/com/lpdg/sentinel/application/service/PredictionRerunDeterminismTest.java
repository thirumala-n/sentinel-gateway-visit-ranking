package com.lpdg.sentinel.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.PredictionWeek;
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
 * Validates Section 13 requirement:
 * Rerun semantics, explicit cache eviction, freshness metadata, and determinism.
 */
class PredictionRerunDeterminismTest {

    private GatewayPredictionApplicationService predictionService;

    @BeforeEach
    void setUp() throws Exception {
        File dataDir = new File("data");
        Assumptions.assumeTrue(
                dataDir.exists() && new File(dataDir, "telemetry").exists(),
                "Skipping test: real data directory not present");

        SentinelProperties properties = new SentinelProperties("data", 15, 28, 7, 4, "risk-based");
        DuckDbConfiguration duckDbConfig = new DuckDbConfiguration();
        DataSource dataSource = duckDbConfig.duckDbDataSource();

        DuckDbGatewayRepository gatewayRepo = new DuckDbGatewayRepository(dataSource, properties);
        DuckDbTelemetryRepository telemetryRepo = new DuckDbTelemetryRepository(dataSource, properties);
        CsvMeterReadRepository meterReadRepo = new CsvMeterReadRepository(properties);

        FeatureExtractionService featureService = new FeatureExtractionService(gatewayRepo, telemetryRepo, meterReadRepo);
        DecisionExplanationService explanationService = new DecisionExplanationService();
        RiskBasedRankingStrategy rankingStrategy = new RiskBasedRankingStrategy();

        predictionService = new GatewayPredictionApplicationService(
                featureService, rankingStrategy, explanationService, properties);
    }

    @Test
    void rerun_forcesRecomputation_andMaintainsBitwiseDeterminism() throws Exception {
        PredictionWeek week = new PredictionWeek(LocalDate.of(2026, 3, 9));

        // 1. Initial computation
        WeeklyPredictionsResult run1 = predictionService.getPredictions(week);
        assertThat(run1).isNotNull();
        assertThat(run1.items()).hasSize(15);

        // 2. Cached lookup returns identical instance
        WeeklyPredictionsResult cached = predictionService.getPredictions(week);
        assertThat(cached).isSameAs(run1);

        // Small pause to guarantee different Instant.now()
        Thread.sleep(10);

        // 3. Explicit rerun forces recomputation
        WeeklyPredictionsResult rerun = predictionService.rerun(week);
        assertThat(rerun).isNotNull();
        assertThat(rerun).isNotSameAs(run1);
        assertThat(rerun.generatedAt()).isAfterOrEqualTo(run1.generatedAt());

        // 4. Assert determinism: exact same 15 items, scores, reasons, and ranks
        assertThat(rerun.items()).hasSize(15);
        for (int i = 0; i < 15; i++) {
            var item1 = run1.items().get(i);
            var itemRerun = rerun.items().get(i);

            assertThat(itemRerun.rank()).isEqualTo(item1.rank());
            assertThat(itemRerun.gatewayId()).isEqualTo(item1.gatewayId());
            assertThat(itemRerun.score()).isEqualTo(item1.score());
            assertThat(itemRerun.reason()).isEqualTo(item1.reason());
            assertThat(itemRerun.fallback()).isEqualTo(item1.fallback());
        }
    }
}
