package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.util.List;

/**
 * Computes F01 — Flagged Offline Hours.
 *
 * <p>Definition (from FEATURE_SPEC.md):
 *
 * <pre>
 *   F01 = SUM(CASE WHEN offline_duration_sec > (mu_28d + 3.0 * sigma_eff) THEN 1 ELSE 0 END)
 * </pre>
 *
 * <p>where the sum is over the 7-day scoring window {@code [T - 7d, T)}.
 *
 * <h3>Sigma Floor (DQ-08)</h3>
 *
 * <p>sigma_eff = MAX(sigma_28d, 60.0 seconds) prevents false flags on stable gateways
 * whose baseline sigma is 0. Without the floor, any 1-second disconnect would exceed the
 * threshold of {@code mu + 3 * 0 = 0}.
 *
 * <h3>Decision</h3>
 *
 * <p>PRIMARY — direct indicator of connectivity failure hours.
 */
public final class F01FlaggedOfflineHoursComputer implements FeatureComputer {

    @Override
    public void compute(
            GatewayFeatures.Builder builder,
            List<TelemetryRow> scoringRows,
            List<TelemetryRow> baselineRows,
            BaselineStats offlineStats,
            BaselineStats rebootStats) {

        double threshold = offlineStats.threshold(BaselineStatsComputer.SIGMA_MIN_OFFLINE_SEC);

        int flagged = 0;
        for (TelemetryRow row : scoringRows) {
            if (row.offlineDurationSec() > threshold) {
                flagged++;
            }
        }

        builder.f01FlaggedOfflineHours(flagged);
    }
}
