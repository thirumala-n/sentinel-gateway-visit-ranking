package com.lpdg.sentinel.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import com.lpdg.sentinel.infrastructure.configuration.DuckDbConfiguration;
import com.lpdg.sentinel.infrastructure.csv.CsvMeterReadRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbGatewayRepository;
import com.lpdg.sentinel.infrastructure.duckdb.DuckDbTelemetryRepository;
import com.lpdg.sentinel.web.validation.PredictionWeekParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test verifying that unseen future months (e.g. April 2026) and dynamic
 * telemetry partition counts (&gt; 8 partitions) are processed end-to-end without
 * rejection or hardcoded March 2026 ceilings.
 *
 * <p>Exercises the real production path:
 * dynamic partition discovery &rarr; week parsing &rarr; repositories &rarr;
 * feature extraction &rarr; RankingContext &rarr; RankingStrategy &rarr; prediction output.
 */
class UnseenMonthIntegrationTest {

    private static final String FAILING_GW = "02D3289B907C";
    private static final String HEALTHY_GW = "0639EA5602C1";

    @Test
    @DisplayName("Production pipeline dynamically discovers 9th partition and ranks unseen April 2026 week")
    void unseenMonthApril2026_processedEndToEndThroughProductionPipeline(@TempDir Path tempDir) throws Exception {
        Path telemetryDir = tempDir.resolve("telemetry");
        Path marchPartition = telemetryDir.resolve("month=2026-03");
        Path aprilPartition = telemetryDir.resolve("month=2026-04");
        Files.createDirectories(marchPartition);
        Files.createDirectories(aprilPartition);

        // 1. Create gateway master data with active gateways on 2026-04-06
        String masterCsv = """
                gateway_id,tenant,site_type,region,hw_model,antenna_type,fw_version,fw_updated_on,installed_on,decommissioned_on,n_meters_installed
                02:D3:28:9B:90:7C,tenant_a,Aussenmast,Bayern,GW-2100,Omni 3dBi,3.3.1,,2022-01-01,,200
                06:39:EA:56:02:C1,tenant_a,Aussenmast,Niedersachsen,GW-2100,Omni 3dBi,3.3.1,,2022-01-01,,250
                """;
        Files.writeString(tempDir.resolve("gateway_master.csv"), masterCsv);

        DuckDbConfiguration duckDbConfig = new DuckDbConfiguration();
        DataSource dataSource = duckDbConfig.duckDbDataSource();

        // 2. Populate March 2026 partition (covers baseline start 2026-03-09 to 2026-03-31)
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            String marchPartPath = marchPartition.resolve("part-0.parquet").toString().replace("\\", "/");
            stmt.execute(String.format(
                    """
                    COPY (
                        SELECT
                            '02D3289B907C' AS gateway_id,
                            strftime(TIMESTAMP '2026-03-09 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(0.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 552) t(i)
                        UNION ALL
                        SELECT
                            '0639EA5602C1' AS gateway_id,
                            strftime(TIMESTAMP '2026-03-09 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(0.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 552) t(i)
                    ) TO '%s' (FORMAT PARQUET)
                    """,
                    marchPartPath));

            // 3. Populate April 2026 partition (9th partition, covers scoring start 2026-04-01 to 2026-04-06)
            // FAILING_GW experiences 100% outage in April; HEALTHY_GW is completely healthy
            String aprilPartPath = aprilPartition.resolve("part-0.parquet").toString().replace("\\", "/");
            stmt.execute(String.format(
                    """
                    COPY (
                        SELECT
                            '02D3289B907C' AS gateway_id,
                            strftime(TIMESTAMP '2026-04-01 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(3600.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 120) t(i)
                        UNION ALL
                        SELECT
                            '0639EA5602C1' AS gateway_id,
                            strftime(TIMESTAMP '2026-04-01 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(0.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 120) t(i)
                    ) TO '%s' (FORMAT PARQUET)
                    """,
                    aprilPartPath));
        }

        // 4. Initialize production pipeline against the dynamic directory
        String dataDirPath = tempDir.toString().replace("\\", "/");
        SentinelProperties properties = new SentinelProperties(dataDirPath, 15, 28, 7, 8, "risk-based");

        // 5. Verify dynamic discovery: parser discovers April without hardcoded March ceiling
        PredictionWeekParser parser = new PredictionWeekParser(properties);
        assertThat(parser.getLatestSupportedMonday())
                .as("Latest supported Monday must dynamically extend to April 2026")
                .isAfterOrEqualTo(LocalDate.of(2026, 4, 6));

        assertThatNoException().isThrownBy(() -> parser.parse("2026-04-06"));
        PredictionWeek aprilWeek = parser.parse("2026-04-06");

        // 6. Execute full production pipeline
        DuckDbGatewayRepository gatewayRepo = new DuckDbGatewayRepository(dataSource, properties);
        DuckDbTelemetryRepository telemetryRepo = new DuckDbTelemetryRepository(dataSource, properties);
        CsvMeterReadRepository meterReadRepo = new CsvMeterReadRepository(properties);
        FeatureExtractionService featureService =
                new FeatureExtractionService(gatewayRepo, telemetryRepo, meterReadRepo);
        RiskBasedRankingStrategy rankingStrategy = new RiskBasedRankingStrategy();
        DecisionExplanationService explanationService = new DecisionExplanationService();

        GatewayPredictionApplicationService appService = new GatewayPredictionApplicationService(
                featureService, rankingStrategy, explanationService, properties);

        WeeklyPredictionsResult result = appService.getPredictions(aprilWeek);

        // 7. Verify predictions output
        assertThat(result).isNotNull();
        assertThat(result.week()).isEqualTo(aprilWeek);
        assertThat(result.items()).isNotEmpty();

        // FAILING_GW must rank #1 due to massive April outage
        WeeklyPredictionsResult.PredictionRecord topRecord = result.items().get(0);
        assertThat(topRecord.gatewayId()).isEqualTo(GatewayId.of(FAILING_GW));
        assertThat(topRecord.rank()).isEqualTo(1);
        assertThat(topRecord.score()).isGreaterThan(20.0);
        assertThat(topRecord.reason()).contains("continuous outage");
    }
}
