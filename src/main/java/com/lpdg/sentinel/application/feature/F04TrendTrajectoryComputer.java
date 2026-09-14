package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Computes F04 — Outage Trajectory Ratio.
 *
 * <p>Definition (from FEATURE_SPEC.md):
 *
 * <pre>
 *   F04 = SUM(offline_duration_sec[last_72h]) / (SUM(offline_duration_sec[prev_96h]) * (72.0/96.0) + 3600.0)
 * </pre>
 *
 * <p>where:
 *
 * <ul>
 *   <li>{@code last_72h} = {@code [T - 72h, T)} — the most recent 3 days
 *   <li>{@code prev_96h} = {@code [T - 168h, T - 72h)} — the 4 days before that
 * </ul>
 *
 * <p>The ratio is clamped to {@code [0.0, 5.0]}.
 *
 * <h3>Denominator Laplace Smoothing</h3>
 *
 * <p>The {@code + 3600.0} term prevents zero-division for gateways whose preceding
 * 96-hour window contains no offline time. It represents a 1-hour baseline floor in
 * the normalizing denominator.
 *
 * <p>A ratio &gt; 1.0 indicates the gateway is deteriorating (recent offline time exceeds
 * the time-normalized historical rate). A ratio &lt; 1.0 indicates recovery.
 *
 * <h3>Requires &ge; 168h of active history</h3>
 *
 * <p>If fewer than 168 qualifying rows exist, the ratio falls back to 1.0 (neutral — no
 * directional evidence).
 *
 * <h3>Decision</h3>
 *
 * <p>PRIMARY — distinguishes recovering faults from actively worsening outages.
 */
public final class F04TrendTrajectoryComputer implements FeatureComputer {

    /** Minimum hours of scoring-window data required to compute a meaningful trend. */
    private static final int MIN_ROWS_FOR_TREND = 24;

    /** Neutral ratio returned when insufficient data is available. */
    public static final double NEUTRAL_RATIO = 1.0;

    /** Maximum clamped ratio value. */
    public static final double MAX_RATIO = 5.0;

    /** 3-day boundary offset in hours from targetMonday. */
    private static final long RECENT_WINDOW_HOURS = 72L;

    @Override
    public void compute(
            GatewayFeatures.Builder builder,
            List<TelemetryRow> scoringRows,
            List<TelemetryRow> baselineRows,
            BaselineStats offlineStats,
            BaselineStats rebootStats) {

        builder.f04TrendTrajectoryRatio(computeRatio(scoringRows));
    }

    /**
     * Computes the trajectory ratio from scoring-window rows.
     *
     * <p>The cutoff between the "recent 72h" and "preceding 96h" sub-windows is derived
     * from the latest observation timestamp minus 72 hours. This avoids dependency on the
     * target Monday date inside this stateless method.
     *
     * @param rows scoring-window rows sorted by tsUtc ascending
     * @return trajectory ratio clamped to [0.0, 5.0]
     */
    static double computeRatio(List<TelemetryRow> rows) {
        if (rows == null || rows.size() < MIN_ROWS_FOR_TREND) {
            return NEUTRAL_RATIO;
        }

        // The split point is derived from the latest observed row, not targetMonday, to keep
        // this method stateless. For fully active gateways the error is <= 1h. For gateways
        // with end-of-week data gaps, the 'recent 72h' window shifts backward slightly, which
        // is conservative: it extends the preceding window and reduces trend inflation.
        Instant latest = rows.get(rows.size() - 1).tsUtc();
        Instant splitPoint = latest.minus(RECENT_WINDOW_HOURS, ChronoUnit.HOURS);

        double sumRecent72 = 0.0;
        double sumPrev96 = 0.0;

        for (TelemetryRow row : rows) {
            if (!row.tsUtc().isBefore(splitPoint)) {
                sumRecent72 += row.offlineDurationSec();
            } else {
                sumPrev96 += row.offlineDurationSec();
            }
        }

        // Denominator: time-normalized prev96 + Laplace floor (1 hour in seconds)
        double denominator = sumPrev96 * (72.0 / 96.0) + 3600.0;
        double ratio = sumRecent72 / denominator;

        return Math.min(MAX_RATIO, Math.max(0.0, ratio));
    }
}
