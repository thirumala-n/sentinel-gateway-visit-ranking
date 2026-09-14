package com.lpdg.sentinel.infrastructure.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import com.lpdg.sentinel.infrastructure.configuration.DuckDbConfiguration;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test verifying the Hive partition glob and timestamp format bug fix.
 *
 * <h3>Bug Description</h3>
 *
 * <p>1. Telemetry parquet files are stored in Hive-partitioned subdirectories
 * ({@code telemetry/month=YYYY-MM/part-0.parquet}). The original glob
 * {@code telemetry/*.parquet} matched zero files, returning empty results for all queries.
 *
 * <p>2. {@code ts_utc} is stored as a VARCHAR in the Parquet files. Comparing it against
 * {@code TIMESTAMPTZ ?} caused a DuckDB Binder Error: {@code Cannot compare values of type
 * VARCHAR and type TIMESTAMP WITH TIME ZONE}.
 *
 * <p>This regression test ensures both issues remain permanently fixed.
 */
class DuckDbTelemetryRepositoryRegressionTest {

    private DuckDbTelemetryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        File dataDir = new File("data/telemetry");
        Assumptions.assumeTrue(
                dataDir.exists() && dataDir.isDirectory(),
                "Skipping regression test: data/telemetry directory not available");

        DuckDbConfiguration config = new DuckDbConfiguration();
        DataSource ds = config.duckDbDataSource();
        SentinelProperties props = new SentinelProperties("data", 15, 28, 7, 4, "risk-based");

        repository = new DuckDbTelemetryRepository(ds, props);
    }

    @Test
    void regression_hivePartitionGlobAndTimestampComparisonLoadsRealTelemetry() {
        // Query August 2025 window for known active gateway 0639EA5602C1
        GatewayId gw = GatewayId.of("0639EA5602C1");
        LocalDate start = LocalDate.of(2025, 8, 1);
        LocalDate end = LocalDate.of(2025, 8, 8);

        List<TelemetryRow> rows = repository.findByGatewayAndWindow(gw, start, end);

        // Must not be empty (proves Hive glob and timestamp comparison work)
        assertThat(rows).isNotEmpty();
        assertThat(rows.size()).isLessThanOrEqualTo(168); // At most 7 days * 24 hours

        for (TelemetryRow row : rows) {
            assertThat(row.gatewayId()).isEqualTo(gw);
            assertThat(row.offlineDurationSec()).isBetween(0.0, 3600.0);
            assertThat(row.rebootCnt()).isGreaterThanOrEqualTo(0.0);
            assertThat(row.disconnectionCnt()).isGreaterThanOrEqualTo(0);
            assertThat(row.tsUtc()).isNotNull();
        }
    }

    @Test
    void regression_fleetBatchQueryRetrievesMultipleGateways() {
        LocalDate start = LocalDate.of(2025, 8, 1);
        LocalDate end = LocalDate.of(2025, 8, 8);

        Map<GatewayId, List<TelemetryRow>> fleetMap = repository.findFleetByWindow(start, end);

        assertThat(fleetMap).isNotEmpty();
        assertThat(fleetMap.size()).isGreaterThan(200); // 280 gateways active in August 2025
    }
}
