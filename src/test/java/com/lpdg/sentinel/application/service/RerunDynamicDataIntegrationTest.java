package com.lpdg.sentinel.application.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates NEXORA Phase 3 Critical Live Session Requirement:
 * When new telemetry partitions are dropped into the data directory while the service
 * is running, calling POST /rerun WITHOUT restarting the application dynamically reads
 * the new data and updates rankings.
 */
class RerunDynamicDataIntegrationTest {

    private static final String GW_A = "02D3289B907C";
    private static final String GW_B = "0639EA5602C1";

    @Test
    void rerun_picksUpNewParquetPartition_withoutRestartingApplication(@TempDir Path tempDir) throws Exception {
        Path telemetryDir = tempDir.resolve("telemetry");
        Path month1Dir = telemetryDir.resolve("month=2026-02");
        Files.createDirectories(month1Dir);

        // 1. Create gateway_master.csv in tempDir
        String masterCsv = """
                gateway_id,n_meters_installed,installed_on,decommissioned_on
                02:D3:28:9B:90:7C,200,2022-01-01,
                06:39:EA:56:02:C1,250,2022-01-01,
                """;
        Files.writeString(tempDir.resolve("gateway_master.csv"), masterCsv);

        DuckDbConfiguration duckDbConfig = new DuckDbConfiguration();
        DataSource dataSource = duckDbConfig.duckDbDataSource();

        // 2. Populate month=2026-02 parquet via DuckDB COPY
        // GW_A is healthy (0 offline), GW_B is moderately degraded (20 offline hours)
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            Path part1 = month1Dir.resolve("part-0.parquet");
            String part1Path = part1.toString().replace("\\", "/");
            stmt.execute(String.format(
                    """
                    COPY (
                        SELECT
                            '02D3289B907C' AS gateway_id,
                            strftime(TIMESTAMP '2026-02-15 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(0.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 336) t(i)
                        UNION ALL
                        SELECT
                            '0639EA5602C1' AS gateway_id,
                            strftime(TIMESTAMP '2026-02-15 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(0.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 336) t(i)
                    ) TO '%s' (FORMAT PARQUET)
                    """,
                    part1Path));
        }

        // 3. Initialize Sentinel Service against tempDir
        SentinelProperties properties = new SentinelProperties(
                tempDir.toString().replace("\\", "/"), 15, 28, 7, 8, "risk-based");
        DuckDbGatewayRepository gatewayRepo = new DuckDbGatewayRepository(dataSource, properties);
        DuckDbTelemetryRepository telemetryRepo = new DuckDbTelemetryRepository(dataSource, properties);
        CsvMeterReadRepository meterReadRepo = new CsvMeterReadRepository(properties);

        FeatureExtractionService featureService =
                new FeatureExtractionService(gatewayRepo, telemetryRepo, meterReadRepo);
        DecisionExplanationService explanationService = new DecisionExplanationService();
        RiskBasedRankingStrategy rankingStrategy = new RiskBasedRankingStrategy();

        GatewayPredictionApplicationService service = new GatewayPredictionApplicationService(
                featureService, rankingStrategy, explanationService, properties);

        PredictionWeek week = new PredictionWeek(LocalDate.of(2026, 3, 2));

        // Initial ranking: both gateways are nominal/healthy (0 offline)
        WeeklyPredictionsResult initialResult = service.getPredictions(week);
        assertThat(initialResult).isNotNull();
        assertThat(initialResult.items()).hasSize(2);
        assertThat(initialResult.items().get(0).score()).isLessThan(1.0);

        // 4. SIMULATE LIVE SESSION: Drop a NEW month partition while service is running!
        // month=2026-03 where GW_A suffers a complete 168-hour failure
        Path month2Dir = telemetryDir.resolve("month=2026-03");
        Files.createDirectories(month2Dir);

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            Path part2 = month2Dir.resolve("part-0.parquet");
            String part2Path = part2.toString().replace("\\", "/");
            stmt.execute(String.format(
                    """
                    COPY (
                        SELECT
                            '02D3289B907C' AS gateway_id,
                            strftime(TIMESTAMP '2026-02-23 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(3600.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 168) t(i)
                        UNION ALL
                        SELECT
                            '0639EA5602C1' AS gateway_id,
                            strftime(TIMESTAMP '2026-02-23 00:00:00' + INTERVAL (i) HOUR, '%%Y-%%m-%%dT%%H:%%M:%%SZ') AS ts_utc,
                            CAST(0.0 AS DOUBLE) AS offline_duration_sec,
                            CAST(0.0 AS DOUBLE) AS reboot_cnt,
                            CAST(0 AS INTEGER) AS disconnection_cnt
                        FROM range(0, 168) t(i)
                    ) TO '%s' (FORMAT PARQUET)
                    """,
                    part2Path));
        }

        // 5. Trigger rerun WITHOUT RESTARTING THE APPLICATION
        WeeklyPredictionsResult updatedResult = service.rerun(week);

        // 6. Verify that the new data was dynamically read from disk
        assertThat(updatedResult).isNotNull();
        assertThat(updatedResult.items()).isNotEmpty();

        GatewayId updatedTop = updatedResult.items().get(0).gatewayId();
        assertThat(updatedTop)
                .as("GW_A suffered 168h failure in the newly dropped parquet file and must now be ranked #1")
                .isEqualTo(GatewayId.of(GW_A));

        assertThat(updatedResult.items().get(0).score())
                .isGreaterThan(initialResult.items().get(0).score());
    }
}
