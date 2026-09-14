package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.util.List;

/**
 * Strategy interface for computing a single feature group from scoring-window telemetry
 * and pre-computed baseline statistics.
 *
 * <p>Each implementation is responsible for exactly the feature(s) declared in
 * {@code docs/FEATURE_SPEC.md}. Implementations must be stateless and deterministic:
 * given the same inputs they must always produce the same output.
 *
 * <p>Implementations must never:
 *
 * <ul>
 *   <li>Load data from repositories.
 *   <li>Access the current system time.
 *   <li>Produce NaN or Infinity in their output values.
 * </ul>
 */
@FunctionalInterface
public interface FeatureComputer {

    /**
     * Computes feature value(s) and writes them into {@code builder}.
     *
     * @param builder mutable builder being populated for this gateway
     * @param scoringRows deduplicated, bounds-clamped telemetry rows for the 7-day scoring
     *     window; ordered by {@code tsUtc} ascending; never null; may be empty
     * @param baselineRows deduplicated, bounds-clamped telemetry rows for the full 28-day
     *     baseline window; ordered by {@code tsUtc} ascending; never null; may be empty
     * @param offlineStats 28-day baseline statistics for {@code offline_duration_sec}
     * @param rebootStats 28-day baseline statistics for {@code reboot_cnt}
     */
    void compute(
            com.lpdg.sentinel.domain.feature.GatewayFeatures.Builder builder,
            List<TelemetryRow> scoringRows,
            List<TelemetryRow> baselineRows,
            BaselineStats offlineStats,
            BaselineStats rebootStats);
}
