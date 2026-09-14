package com.lpdg.sentinel.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.BaselineRankingStrategy;
import com.lpdg.sentinel.domain.ranking.RankedGatewayDecision;
import com.lpdg.sentinel.domain.ranking.RankingContext;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import com.lpdg.sentinel.infrastructure.configuration.DuckDbConfiguration;
import com.lpdg.sentinel.infrastructure.csv.CsvMeterReadRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbGatewayRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbTelemetryRepository;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test executing historical feature extraction and ranking against
 * real competition data.
 */
class HistoricalBacktestIntegrationTest {

    private FeatureExtractionService extractionService;
    private BaselineRankingStrategy baselineStrategy;
    private RiskBasedRankingStrategy riskStrategy;

    @BeforeEach
    void setUp() throws Exception {
        File dataDir = new File("data");
        Assumptions.assumeTrue(
                dataDir.exists() && new File(dataDir, "telemetry").exists(),
                "Skipping integration test: data directory not available");

        SentinelProperties properties = new SentinelProperties("data", 15, 28, 7, 4, "risk-based");
        DuckDbConfiguration duckDbConfig = new DuckDbConfiguration();
        DataSource dataSource = duckDbConfig.duckDbDataSource();

        DuckDbGatewayRepository gatewayRepo = new DuckDbGatewayRepository(dataSource, properties);
        DuckDbTelemetryRepository telemetryRepo = new DuckDbTelemetryRepository(dataSource, properties);
        CsvMeterReadRepository meterReadRepo = new CsvMeterReadRepository(properties);

        extractionService = new FeatureExtractionService(gatewayRepo, telemetryRepo, meterReadRepo);
        baselineStrategy = new BaselineRankingStrategy();
        riskStrategy = new RiskBasedRankingStrategy();
    }

    @Test
    void historicalWeek_2026_03_09_executesEndToEndAndMatchesEvidence() {
        PredictionWeek week = new PredictionWeek(LocalDate.of(2026, 3, 9));

        RankingContext context = extractionService.extractFeatures(week);

        assertThat(context).isNotNull();
        assertThat(context.size()).isGreaterThanOrEqualTo(300); // 304 active gateways

        // Baseline ranking
        List<RankedGatewayDecision> baselineDecisions = baselineStrategy.rank(context, 15);
        assertThat(baselineDecisions).hasSize(15);
        for (int i = 0; i < 15; i++) {
            assertThat(baselineDecisions.get(i).rank()).isEqualTo(i + 1);
            assertThat(Double.isNaN(baselineDecisions.get(i).score())).isFalse();
        }

        // Risk-based ranking
        List<RankedGatewayDecision> riskDecisions = riskStrategy.rank(context, 15);
        assertThat(riskDecisions).hasSize(15);
        for (int i = 0; i < 15; i++) {
            assertThat(riskDecisions.get(i).rank()).isEqualTo(i + 1);
            assertThat(Double.isNaN(riskDecisions.get(i).score())).isFalse();
            assertThat(riskDecisions.get(i).score()).isGreaterThanOrEqualTo(0.0);
        }

        // Chronic dead gateway 02D3289B907C (168h dead) must be ranked #1 in risk strategy
        assertThat(riskDecisions.get(0).gatewayId()).isEqualTo(GatewayId.of("02D3289B907C"));
        assertThat(riskDecisions.get(0).score()).isGreaterThan(80.0);

        // Determinism test: repeated ranking on same context produces byte-for-byte identical results
        List<RankedGatewayDecision> run2 = riskStrategy.rank(context, 15);
        for (int i = 0; i < 15; i++) {
            assertThat(run2.get(i).gatewayId()).isEqualTo(riskDecisions.get(i).gatewayId());
            assertThat(run2.get(i).score()).isEqualTo(riskDecisions.get(i).score());
            assertThat(run2.get(i).rationale()).isEqualTo(riskDecisions.get(i).rationale());
        }
    }
}
