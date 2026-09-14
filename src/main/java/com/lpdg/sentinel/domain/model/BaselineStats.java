package com.lpdg.sentinel.domain.model;

/**
 * Immutable per-gateway per-metric baseline statistics computed over the 28-day window
 * {@code [T - 28 days, T)}.
 *
 * <p>The scoring window {@code [T - 7 days, T)} is the trailing portion of this baseline
 * window and is NOT excluded from these statistics. Both windows terminate at the same
 * exclusive boundary {@code T} (targetMonday).
 *
 * <h3>Zero-Variance Floor (DQ-08)</h3>
 *
 * <p>Metrics like {@code reboot_cnt} (98.4% zero) and {@code offline_duration_sec} (77.3%
 * zero) have {@code sigma ≈ 0} for stable gateways. Direct division by zero creates
 * {@code Infinity} or {@code NaN}, and a threshold of {@code mu + 3 * 0 = mu} falsely
 * flags any tiny blip. The {@link #sigmaEff(double)} method enforces the floor:
 *
 * <pre>
 *   sigma_eff = MAX(sigma, sigma_min)
 * </pre>
 *
 * <p>where {@code sigma_min} is metric-specific (60.0 seconds for offline duration;
 * 0.5 reboots for reboot count).
 *
 * <h3>Cold-Start (DQ-06)</h3>
 *
 * <p>Gateways with fewer than {@link #COLD_START_THRESHOLD_HOURS} hours of history cannot
 * produce a statistically robust baseline. The {@link #coldStart} flag is set; callers
 * should substitute fleet-wide median parameters.
 *
 * @param mean arithmetic mean over the baseline window
 * @param stdDev sample standard deviation over the baseline window (may be 0.0 for stable
 *     gateways)
 * @param observationCount number of deduplicated hourly observations in the baseline window
 * @param coldStart {@code true} if the gateway has fewer than 336 hours of history
 */
public record BaselineStats(double mean, double stdDev, int observationCount, boolean coldStart) {

    /** Minimum hours of history required for a reliable 28-day baseline (14 days × 24h). */
    public static final int COLD_START_THRESHOLD_HOURS = 336;

    public BaselineStats {
        if (Double.isNaN(mean) || Double.isInfinite(mean)) {
            throw new IllegalArgumentException("mean must be a finite number, got: " + mean);
        }
        if (Double.isNaN(stdDev) || Double.isInfinite(stdDev) || stdDev < 0.0) {
            throw new IllegalArgumentException(
                    "stdDev must be a finite non-negative number, got: " + stdDev);
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException(
                    "observationCount must be >= 0, got: " + observationCount);
        }
    }

    /**
     * Returns the effective standard deviation with a floor applied.
     *
     * <p>Prevents division-by-zero and NaN propagation for gateways with perfectly stable
     * baselines. The floor value ({@code sigmaMin}) is metric-specific and must be provided
     * by the caller.
     *
     * <p>Formula: {@code sigma_eff = MAX(stdDev, sigmaMin)}
     *
     * @param sigmaMin minimum allowed standard deviation (must be &gt; 0)
     * @return effective standard deviation &ge; {@code sigmaMin}
     * @throws IllegalArgumentException if {@code sigmaMin} is not positive
     */
    public double sigmaEff(double sigmaMin) {
        if (sigmaMin <= 0.0 || Double.isNaN(sigmaMin) || Double.isInfinite(sigmaMin)) {
            throw new IllegalArgumentException(
                    "sigmaMin must be a positive finite number, got: " + sigmaMin);
        }
        return Math.max(stdDev, sigmaMin);
    }

    /**
     * Returns the 3-sigma anomaly threshold for this metric.
     *
     * <p>Formula: {@code mu + 3 * sigma_eff}
     *
     * @param sigmaMin floor to apply via {@link #sigmaEff(double)}
     * @return anomaly threshold value above which an hourly observation is considered anomalous
     */
    public double threshold(double sigmaMin) {
        return mean + 3.0 * sigmaEff(sigmaMin);
    }

    /**
     * Creates a zero-observation {@link BaselineStats} instance representing a gateway with no
     * history. Mean and stdDev are both 0.
     *
     * @return cold-start baseline with zero observations
     */
    public static BaselineStats empty() {
        return new BaselineStats(0.0, 0.0, 0, true);
    }
}
