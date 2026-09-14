package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Computes F03 — Maximum Consecutive Offline Hours.
 *
 * <p>Definition (from FEATURE_SPEC.md):
 *
 * <pre>
 *   F03 = MAX_RUN(offline_duration_sec >= 3000)
 * </pre>
 *
 * <p>where the run is over the 7-day scoring window {@code [T - 7d, T)}.
 *
 * <h3>Persistence Definition — Critical Rule</h3>
 *
 * <p>A consecutive run is defined over ACTUAL hourly timestamp adjacency. Two observations
 * are consecutive only if their timestamps are exactly 1 hour apart. A gap of 2 or more
 * hours between observations breaks the run unconditionally — the missing hour is NOT
 * bridged and NOT treated as either offline or online.
 *
 * <p>Example: If rows exist at 10:00 and 12:00 (no row at 11:00), the run of any
 * qualifying hour at 10:00 is broken before 12:00. The run resets to 0 before processing
 * the 12:00 row.
 *
 * <h3>Outage Threshold</h3>
 *
 * <p>An hour qualifies as "near-total outage" when:
 * {@code offline_duration_sec >= 3000} (83.3% of the hour is offline).
 *
 * <h3>Decision</h3>
 *
 * <p>PRIMARY — distinguishes chronic hard-down gateways from intermittent transients.
 */
public final class F03MaxConsecutiveOfflineComputer implements FeatureComputer {

    /** Offline threshold: 3000 seconds out of 3600 = 83.3% of the hour offline. */
    public static final double NEAR_TOTAL_OUTAGE_THRESHOLD_SEC = 3000.0;

    /**
     * Maximum gap in seconds between two observations to still be considered "consecutive".
     * Exactly 1 hour = 3600 seconds.
     */
    private static final long MAX_CONSECUTIVE_GAP_SECONDS = 3600L;

    @Override
    public void compute(
            GatewayFeatures.Builder builder,
            List<TelemetryRow> scoringRows,
            List<TelemetryRow> baselineRows,
            BaselineStats offlineStats,
            BaselineStats rebootStats) {

        builder.f03MaxConsecutiveOfflineHours(computeMaxRun(scoringRows));
    }

    /**
     * Computes the maximum consecutive run of near-total-outage hours.
     *
     * <p>Rows must be pre-sorted by {@code tsUtc} ascending (guaranteed by the repository
     * contract). A run is broken when:
     *
     * <ul>
     *   <li>The current hour does not qualify ({@code offline_duration_sec < 3000}), OR
     *   <li>The gap to the previous hour is greater than 3600 seconds (missing hour).
     * </ul>
     *
     * @param rows sorted scoring-window telemetry rows
     * @return maximum consecutive qualifying hours
     */
    static int computeMaxRun(List<TelemetryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        int maxRun = 0;
        int currentRun = 0;
        Instant previousTs = null;

        for (TelemetryRow row : rows) {
            boolean qualifies = row.offlineDurationSec() >= NEAR_TOTAL_OUTAGE_THRESHOLD_SEC;

            if (!qualifies) {
                // Non-qualifying hour always breaks the run
                currentRun = 0;
                previousTs = row.tsUtc();
                continue;
            }

            // Qualifying hour: check temporal adjacency
            if (previousTs == null) {
                // First qualifying observation
                currentRun = 1;
            } else {
                long gapSeconds = ChronoUnit.SECONDS.between(previousTs, row.tsUtc());
                if (gapSeconds <= MAX_CONSECUTIVE_GAP_SECONDS) {
                    // Truly adjacent: extend the run
                    currentRun++;
                } else {
                    // Gap detected — missing hour breaks the run
                    currentRun = 1;
                }
            }

            maxRun = Math.max(maxRun, currentRun);
            previousTs = row.tsUtc();
        }

        return maxRun;
    }
}
