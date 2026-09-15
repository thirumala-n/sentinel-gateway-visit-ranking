package com.lpdg.sentinel.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.application.service.FeatureExtractionService;
import com.lpdg.sentinel.application.service.GatewayPredictionApplicationService;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import com.lpdg.sentinel.infrastructure.configuration.DuckDbConfiguration;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbGatewayRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbTelemetryRepository;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end generation and validation test for NEXORA Part 1 Gate requirements:
 * 1. Exactly 120 rows (15 gateways x 8 weeks).
 * 2. Exactly ranks 1..15 for every week.
 * 3. Zero duplicate (gateway, week) combinations.
 * 4. Numeric, finite, non-empty scores.
 * 5. Non-empty reasons <= 300 characters.
 * 6. Generates root predictions.csv using the actual production pipeline.
 */
class PredictionsCsvGenerationTest {

    public static final List<LocalDate> CANONICAL_8_WEEKS = PredictionWeek.OFFICIAL_COMPETITION_WEEKS;

    private GatewayPredictionApplicationService predictionService;
    private PredictionsCsvExporter csvExporter;

    @BeforeEach
    void setUp() throws Exception {
        File dataDir = new File("data");
        Assumptions.assumeTrue(
                dataDir.exists() && new File(dataDir, "telemetry").exists(),
                "Skipping test: real data directory not present");

        SentinelProperties properties = new SentinelProperties("data", 15, 28, 7, 8, "risk-based");
        DuckDbConfiguration duckDbConfig = new DuckDbConfiguration();
        DataSource dataSource = duckDbConfig.duckDbDataSource();

        DuckDbGatewayRepository gatewayRepo = new DuckDbGatewayRepository(dataSource, properties);
        DuckDbTelemetryRepository telemetryRepo = new DuckDbTelemetryRepository(dataSource, properties);
        CsvMeterReadRepository meterReadRepo = new CsvMeterReadRepository(properties);

        FeatureExtractionService featureService =
                new FeatureExtractionService(gatewayRepo, telemetryRepo, meterReadRepo);
        DecisionExplanationService explanationService = new DecisionExplanationService();
        RiskBasedRankingStrategy rankingStrategy = new RiskBasedRankingStrategy();

        predictionService = new GatewayPredictionApplicationService(
                featureService, rankingStrategy, explanationService, properties);
        csvExporter = new PredictionsCsvExporter();
    }

    @Test
    void generateAndValidate_predictionsCsv_satisfiesAllPart1GateRequirements() throws Exception {
        List<WeeklyPredictionsResult> results = new ArrayList<>();
        for (LocalDate monday : CANONICAL_8_WEEKS) {
            PredictionWeek week = new PredictionWeek(monday);
            WeeklyPredictionsResult result = predictionService.getPredictions(week);
            assertThat(result.items()).hasSize(15);
            results.add(result);
        }

        // Export to string and file
        String csvContent = csvExporter.exportAllToString(results);
        assertThat(csvContent).isNotBlank();

        Path rootPredictionsCsv = Paths.get("predictions.csv");
        csvExporter.exportAllToFile(results, rootPredictionsCsv);
        assertThat(Files.exists(rootPredictionsCsv)).isTrue();

        // Validate structure line by line
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (CSVParser parser = new CSVParser(new StringReader(csvContent), format)) {
            List<CSVRecord> records = parser.getRecords();

            // 1. Exactly 120 rows
            assertThat(records)
                    .as("Must produce exactly 120 prediction rows (15 gateways x 8 weeks)")
                    .hasSize(120);

            Set<String> weekGatewayPairs = new HashSet<>();
            int currentWeekIndex = -1;
            String currentWeek = "";

            for (int i = 0; i < records.size(); i++) {
                CSVRecord row = records.get(i);
                int expectedRank = (i % 15) + 1;

                if (expectedRank == 1) {
                    currentWeekIndex++;
                    currentWeek = CANONICAL_8_WEEKS.get(currentWeekIndex).toString();
                }

                // 2. Week matches expected
                assertThat(row.get("week_start")).isEqualTo(currentWeek);

                // 3. Rank is 1..15 sequential
                int rank = Integer.parseInt(row.get("rank"));
                assertThat(rank).isEqualTo(expectedRank);

                // 4. Gateway ID is non-empty 12-char hex
                String gwId = row.get("gateway_id");
                assertThat(gwId).isNotBlank().matches("^[0-9A-Fa-f]{12}$");

                // 5. Unique per week
                String pair = currentWeek + ":" + gwId;
                assertThat(weekGatewayPairs.add(pair))
                        .as("Duplicate gateway %s in week %s", gwId, currentWeek)
                        .isTrue();

                // 6. Score is numeric, finite, non-empty
                String scoreStr = row.get("score");
                assertThat(scoreStr).isNotBlank();
                double score = Double.parseDouble(scoreStr);
                assertThat(Double.isNaN(score)).isFalse();
                assertThat(Double.isInfinite(score)).isFalse();
                assertThat(score).isGreaterThanOrEqualTo(0.0);

                // 7. Reason is non-empty and <= 300 characters
                String reason = row.get("reason");
                assertThat(reason)
                        .isNotBlank()
                        .hasSizeLessThanOrEqualTo(300);
            }
        }
    }
}
