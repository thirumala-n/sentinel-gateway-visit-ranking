package com.lpdg.sentinel.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import com.lpdg.sentinel.infrastructure.configuration.DuckDbConfiguration;
import com.lpdg.sentinel.infrastructure.csv.CsvMeterReadRepository;
import com.lpdg.sentinel.infrastructure.csv.PredictionsCsvExporter;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbGatewayRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbTelemetryRepository;
import java.io.File;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validates Section 8 requirement:
 * Absolute consistency between REST API predictions and generated predictions.csv.
 *
 * <p>Proves that there is ONE canonical decision result, and that both the API DTOs
 * and the exported CSV agree on:
 * <ul>
 *   <li>week</li>
 *   <li>rank</li>
 *   <li>gateway_id</li>
 *   <li>score</li>
 *   <li>reason</li>
 * </ul>
 */
class PredictionsCsvConsistencyTest {

    private GatewayPredictionApplicationService predictionService;
    private PredictionsCsvExporter csvExporter;

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
        csvExporter = new PredictionsCsvExporter();
    }

    @Test
    void apiPredictionsAndCsvExport_agree100PercentOnAllFields() throws Exception {
        PredictionWeek week = new PredictionWeek(LocalDate.of(2026, 3, 9));

        WeeklyPredictionsResult result = predictionService.getPredictions(week);
        assertThat(result.items()).hasSize(15);

        // Generate CSV output from the canonical result
        String csvOutput = csvExporter.exportToString(result);
        assertThat(csvOutput).isNotBlank();

        // Parse generated CSV
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (CSVParser parser = new CSVParser(new StringReader(csvOutput), format)) {
            List<CSVRecord> records = parser.getRecords();
            assertThat(records).hasSize(15);

            for (int i = 0; i < 15; i++) {
                WeeklyPredictionsResult.PredictionRecord modelItem = result.items().get(i);
                CSVRecord csvRow = records.get(i);

                // 1. Week agreement — CSV must agree with the model's actual target week
                assertThat(csvRow.get("week_start"))
                        .isEqualTo(result.week().targetMonday().toString());

                // 2. Rank agreement
                assertThat(Integer.parseInt(csvRow.get("rank")))
                        .isEqualTo(modelItem.rank())
                        .isEqualTo(i + 1);

                // 3. Gateway ID agreement
                assertThat(csvRow.get("gateway_id"))
                        .isEqualTo(modelItem.gatewayId().value());

                // 4. Score agreement (within 4 decimal places)
                double csvScore = Double.parseDouble(csvRow.get("score"));
                assertThat(csvScore).isCloseTo(modelItem.score(), org.assertj.core.data.Offset.offset(0.0001));

                // 5. Reason agreement (exact string equality)
                assertThat(csvRow.get("reason"))
                        .isEqualTo(modelItem.reason())
                        .isEqualTo(modelItem.explanation().csvReason());
            }
        }
    }
}
