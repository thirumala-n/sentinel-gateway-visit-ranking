package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.util.List;

/**
 * Computes F06 — Cumulative Excess Z-Score.
 *
 * <p>Definition (from FEATURE_SPEC.md):
 *
 * <pre>
 *   F06 = SUM(MAX(0.0, ((X_t - mu_28d) / sigma_eff) - 3.0))
 * </pre>
 *
 * <p>where the sum is over the 7-day scoring window {@code [T - 7d, T)}, applied to the
 * {@code offline_duration_sec} metric.
 *
 * <h3>Hourly Excess Cap</h3>
 *
 * <p>Each hourly excess contribution is capped at 10.0 to prevent a single extreme
 * outlier (e.g., a corrupted 86400-second reading) from dominating the entire score.
 *
 * <h3>Sigma Floor (DQ-08)</h3>
 *
 * <p>sigma_eff = MAX(sigma_28d, 60.0) — same floor as F01 — prevents infinite z-scores
 * on zero-variance gateways.
 *
 * <h3>Interpretation</h3>
 *
 * <p>Unlike F01 which counts binary flags, F06 accumulates the vertical severity of each
 * anomaly. A gateway with one extreme 12-sigma event scores higher than one with twelve
 * borderline 3.01-sigma events, capturing intensity.
 *
 * <h3>Decision</h3>
 *
 * <p>SECONDARY — provides severity depth beyond the binary F01 count.
 */
public final class F06CumulativeExcessZComputer implements FeatureComputer {

    /** Maximum excess z-score contribution per individual hour. */
    public static final double MAX_HOURLY_EXCESS = 10.0;

    @Override
    public void compute(
            GatewayFeatures.Builder builder,
            List<TelemetryRow> scoringRows,
            List<TelemetryRow> baselineRows,
            BaselineStats offlineStats,
            BaselineStats rebootStats) {

        double sigmaEff = offlineStats.sigmaEff(BaselineStatsComputer.SIGMA_MIN_OFFLINE_SEC);
        double mu = offlineStats.mean();

        double cumExcess = 0.0;
        for (TelemetryRow row : scoringRows) {
            double z = (row.offlineDurationSec() - mu) / sigmaEff;
            double excess = Math.max(0.0, z - 3.0);
            excess = Math.min(MAX_HOURLY_EXCESS, excess);
            cumExcess += excess;
        }

        builder.f06CumulativeExcessZ(cumExcess);
    }
}
