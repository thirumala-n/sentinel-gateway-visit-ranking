package com.lpdg.sentinel.domain.model;

/**
 * Confidence level assigned to a gateway's feature set based on available history.
 *
 * <p>Used in {@link com.lpdg.sentinel.domain.feature.GatewayFeatures} to flag gateways
 * where the statistical baseline may be unreliable due to insufficient data.
 *
 * <ul>
 *   <li>{@link #FULL} — &ge; 336 hours (14 days) of history; statistics are reliable.
 *   <li>{@link #LOW_HISTORY} — &lt; 336 hours of history; cold-start gateway. Fleet-wide
 *       median baseline parameters are substituted. Features are computed but should be
 *       treated with caution.
 *   <li>{@link #MISSING_TELEMETRY} — Zero telemetry rows in the scoring window for an
 *       otherwise active gateway. Treated as 100% hard-dead (168 hours of maximum offline
 *       duration). Priority is elevated.
 * </ul>
 */
public enum DataConfidence {

    /** Full statistical history; baseline statistics are reliable. */
    FULL,

    /**
     * Cold-start gateway (&lt; 336 hours of history). Fleet-wide median baseline parameters
     * used. Features computed with degraded confidence.
     */
    LOW_HISTORY,

    /**
     * Active gateway with zero telemetry rows in the scoring window. Treated as completely
     * offline for 168 hours.
     */
    MISSING_TELEMETRY
}
