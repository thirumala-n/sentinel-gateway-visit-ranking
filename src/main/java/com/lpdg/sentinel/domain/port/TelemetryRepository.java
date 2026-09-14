package com.lpdg.sentinel.domain.port;

import com.lpdg.sentinel.domain.model.GatewayId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Port interface for loading deduplicated telemetry observations.
 *
 * <p>Implementations (in the infrastructure layer) query the Parquet telemetry files via
 * DuckDB. They are responsible for:
 *
 * <ul>
 *   <li>Deduplicating gateway-hour pairs via {@code GROUP BY gateway_id, ts_utc}
 *       with {@code MAX(metric)} aggregation (DQ-03).
 *   <li>Clamping physical bounds: {@code offline_duration_sec ∈ [0, 3600]},
 *       {@code disconnection_cnt ≥ 0}, {@code reboot_cnt ≥ 0} (DQ-09).
 *   <li>Enforcing temporal integrity: only rows where {@code ts_utc < cutoff} are returned.
 *   <li>Using projection queries — never {@code SELECT *}.
 * </ul>
 */
public interface TelemetryRepository {

    /**
     * An immutable, deduplicated hourly telemetry row for a single gateway.
     *
     * <p>All metrics have already been deduplicated and bounds-clamped by the infrastructure
     * implementation before this record is constructed.
     *
     * @param gatewayId normalized gateway identifier
     * @param tsUtc hour-precision timestamp in UTC (exclusive end: {@code tsUtc < cutoff})
     * @param offlineDurationSec seconds offline in this hour; clamped to [0, 3600]
     * @param rebootCnt reboot events in this hour; clamped to >= 0
     * @param disconnectionCnt socket disconnection events; clamped to >= 0
     */
    record TelemetryRow(
            GatewayId gatewayId,
            Instant tsUtc,
            double offlineDurationSec,
            double rebootCnt,
            int disconnectionCnt) {}

    /**
     * Returns deduplicated hourly telemetry rows for {@code gatewayId} within the
     * half-open time window {@code [windowStart, windowEnd)}.
     *
     * <p>The caller must ensure {@code windowEnd <= targetMonday} to preserve temporal
     * integrity (no future-data leakage).
     *
     * @param gatewayId normalized gateway identifier
     * @param windowStart inclusive start of the time window (as a date; treated as start of day)
     * @param windowEnd exclusive end of the time window (as a date; treated as start of day)
     * @return ordered list of telemetry rows; ordered by {@code tsUtc} ascending; never null
     */
    List<TelemetryRow> findByGatewayAndWindow(
            GatewayId gatewayId, LocalDate windowStart, LocalDate windowEnd);

    /**
     * Returns deduplicated hourly telemetry rows for all gateways within the half-open
     * time window {@code [windowStart, windowEnd)}, grouped by gateway identifier.
     *
     * <p>Enables single-query batch retrieval for the entire fleet, avoiding repeated
     * parquet file scans.
     *
     * @param windowStart inclusive start of the time window
     * @param windowEnd exclusive end of the time window
     * @return map from gateway ID to ordered list of telemetry rows; never null
     */
    default java.util.Map<GatewayId, List<TelemetryRow>> findFleetByWindow(
            LocalDate windowStart, LocalDate windowEnd) {
        return java.util.Collections.emptyMap();
    }
}
