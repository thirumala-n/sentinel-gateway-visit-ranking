package com.lpdg.sentinel.domain.port;

import com.lpdg.sentinel.domain.model.GatewayId;

/**
 * Port interface for loading historic meter read success rates.
 *
 * <p>Implementations read from {@code meter_read_success.csv}, which contains a 26-week
 * historic snapshot ending on 2026-01-26. This data is intentionally used only as a
 * secondary explanation feature (F07) — NOT as a real-time detector — because it is up
 * to 5 weeks stale relative to the scoring week (DQ-07).
 *
 * <p>If a gateway is absent from the file, the fleet median of 91.0% is returned.
 */
public interface MeterReadRepository {

    /** Fleet-wide median read success rate used as fallback (DQ-07). */
    double FLEET_MEDIAN_SUCCESS_RATE = 91.0;

    /**
     * Returns the historic 26-week average meter read success rate (0–100%) for
     * {@code gatewayId}.
     *
     * <p>If the gateway is not present in {@code meter_read_success.csv}, returns
     * {@link #FLEET_MEDIAN_SUCCESS_RATE}.
     *
     * @param gatewayId normalized gateway identifier
     * @return read success rate in [0.0, 100.0]
     */
    double findSuccessRate(GatewayId gatewayId);
}
