package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.model.BaselineStats;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for computing per-gateway baseline statistics from deduplicated hourly
 * telemetry observations.
 *
 * <p>Baseline window: {@code [T - 28 days, T)} — all observations strictly before
 * {@code targetMonday}.
 *
 * <p>Statistics computed here:
 *
 * <ul>
 *   <li>Arithmetic mean: {@code mu = SUM(X) / N}
 *   <li>Sample standard deviation: {@code sigma = SQRT(SUM((X - mu)^2) / (N - 1))}
 * </ul>
 *
 * <p>When N = 0 (no observations), returns {@link BaselineStats#empty()}.
 * When N = 1, standard deviation is 0.0 (cannot be computed with N-1 denominator).
 */
public final class BaselineStatsComputer {

    /** Sigma floor for offline_duration_sec (seconds). */
    public static final double SIGMA_MIN_OFFLINE_SEC = 60.0;

    /** Sigma floor for reboot_cnt (reboots). */
    public static final double SIGMA_MIN_REBOOT_CNT = 0.5;

    private BaselineStatsComputer() {}

    /**
     * Computes sample mean and standard deviation from a list of raw metric values.
     *
     * <p>The cold-start flag is set when observation count is below
     * {@link BaselineStats#COLD_START_THRESHOLD_HOURS} (336 hours).
     *
     * @param values list of raw metric values (already deduplicated and bounds-clamped)
     * @return {@link BaselineStats} containing mean, stdDev, count, and cold-start flag
     */
    public static BaselineStats compute(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return BaselineStats.empty();
        }

        int n = values.size();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double stdDev = 0.0;
        if (n > 1) {
            double sumSqDiff = values.stream()
                    .mapToDouble(x -> (x - mean) * (x - mean))
                    .sum();
            stdDev = Math.sqrt(sumSqDiff / (n - 1));
        }

        boolean coldStart = n < BaselineStats.COLD_START_THRESHOLD_HOURS;
        return new BaselineStats(mean, stdDev, n, coldStart);
    }

    /**
     * Extracts {@code offline_duration_sec} values from a week's telemetry rows and
     * computes baseline statistics.
     *
     * @param rows deduplicated, bounds-clamped telemetry rows
     * @return baseline statistics for offline duration
     */
    public static BaselineStats computeOfflineStats(
            List<com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow> rows) {
        List<Double> values = rows.stream()
                .map(r -> r.offlineDurationSec())
                .collect(Collectors.toList());
        return compute(values);
    }

    /**
     * Extracts {@code reboot_cnt} values from a week's telemetry rows and computes baseline
     * statistics.
     *
     * @param rows deduplicated, bounds-clamped telemetry rows
     * @return baseline statistics for reboot count
     */
    public static BaselineStats computeRebootStats(
            List<com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow> rows) {
        List<Double> values = rows.stream()
                .map(r -> r.rebootCnt())
                .collect(Collectors.toList());
        return compute(values);
    }
}
