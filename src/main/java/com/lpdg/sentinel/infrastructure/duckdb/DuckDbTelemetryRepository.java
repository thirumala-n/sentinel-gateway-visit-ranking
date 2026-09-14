package com.lpdg.sentinel.infrastructure.duckdb;

import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.port.TelemetryRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * DuckDB-backed implementation of {@link TelemetryRepository}.
 *
 * <p>Reads from Hive-partitioned {@code telemetry/*\/*.parquet} using DuckDB's native Parquet engine.
 * Enforces deduplication, physical bounding, and anti-leakage temporal bounds in SQL.
 */
@Repository
public class DuckDbTelemetryRepository implements TelemetryRepository {

    private static final String SINGLE_GATEWAY_QUERY =
            """
            SELECT
                gateway_id,
                ts_utc,
                LEAST(3600.0, GREATEST(0.0, MAX(offline_duration_sec))) AS offline_duration_sec,
                GREATEST(0.0, MAX(reboot_cnt))                          AS reboot_cnt,
                GREATEST(0,   MAX(disconnection_cnt))                   AS disconnection_cnt
            FROM read_parquet(?)
            WHERE gateway_id = ?
              AND ts_utc >= ?
              AND ts_utc < ?
            GROUP BY gateway_id, ts_utc
            ORDER BY ts_utc ASC
            """;

    private static final String FLEET_QUERY =
            """
            SELECT
                gateway_id,
                ts_utc,
                LEAST(3600.0, GREATEST(0.0, MAX(offline_duration_sec))) AS offline_duration_sec,
                GREATEST(0.0, MAX(reboot_cnt))                          AS reboot_cnt,
                GREATEST(0,   MAX(disconnection_cnt))                   AS disconnection_cnt
            FROM read_parquet(?)
            WHERE ts_utc >= ?
              AND ts_utc < ?
            GROUP BY gateway_id, ts_utc
            ORDER BY gateway_id, ts_utc ASC
            """;

    private final DataSource dataSource;
    private final String dataDir;

    public DuckDbTelemetryRepository(DataSource dataSource, SentinelProperties properties) {
        this.dataSource = dataSource;
        this.dataDir = properties.dataDir();
    }

    private String getParquetGlob() {
        return dataDir + "/telemetry/*/*.parquet";
    }

    @Override
    public List<TelemetryRow> findByGatewayAndWindow(
            GatewayId gatewayId, LocalDate windowStart, LocalDate windowEnd) {

        String parquetGlob = getParquetGlob();
        String startStr = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        String endStr = windowEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toString();

        List<TelemetryRow> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(SINGLE_GATEWAY_QUERY)) {
            stmt.setString(1, parquetGlob);
            stmt.setString(2, gatewayId.value());
            stmt.setString(3, startStr);
            stmt.setString(4, endStr);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Instant tsUtc = Instant.parse(rs.getString("ts_utc"));
                    double offlineSec = rs.getDouble("offline_duration_sec");
                    double rebootCnt = rs.getDouble("reboot_cnt");
                    int disconnectCnt = rs.getInt("disconnection_cnt");

                    result.add(new TelemetryRow(gatewayId, tsUtc, offlineSec, rebootCnt, disconnectCnt));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to load telemetry for gateway " + gatewayId + " from: " + parquetGlob,
                    e);
        }

        return result;
    }

    @Override
    public Map<GatewayId, List<TelemetryRow>> findFleetByWindow(
            LocalDate windowStart, LocalDate windowEnd) {

        String parquetGlob = getParquetGlob();
        String startStr = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        String endStr = windowEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toString();

        Map<GatewayId, List<TelemetryRow>> fleetMap = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(FLEET_QUERY)) {
            stmt.setString(1, parquetGlob);
            stmt.setString(2, startStr);
            stmt.setString(3, endStr);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String rawId = rs.getString("gateway_id");
                    GatewayId gatewayId = GatewayId.of(rawId);
                    Instant tsUtc = Instant.parse(rs.getString("ts_utc"));
                    double offlineSec = rs.getDouble("offline_duration_sec");
                    double rebootCnt = rs.getDouble("reboot_cnt");
                    int disconnectCnt = rs.getInt("disconnection_cnt");

                    fleetMap.computeIfAbsent(gatewayId, k -> new ArrayList<>())
                            .add(new TelemetryRow(gatewayId, tsUtc, offlineSec, rebootCnt, disconnectCnt));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to load fleet telemetry from: " + parquetGlob, e);
        }

        return fleetMap;
    }
}
