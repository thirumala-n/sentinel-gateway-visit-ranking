package com.lpdg.sentinel.application.feature;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.util.List;

/**
 * Computes F02 — Flagged Reboot Hours.
 *
 * <p>Definition (from FEATURE_SPEC.md):
 *
 * <pre>
 *   F02 = SUM(CASE WHEN reboot_cnt > (mu_28d + 3.0 * sigma_eff) THEN 1 ELSE 0 END)
 * </pre>
 *
 * <p>where the sum is over the 7-day scoring window {@code [T - 7d, T)}.
 *
 * <h3>Sigma Floor (DQ-08)</h3>
 *
 * <p>sigma_eff = MAX(sigma_28d, 0.5) prevents false flags on gateways that never
 * rebooted during the baseline (sigma = 0). The floor of 0.5 reboots means the threshold
 * becomes {@code mu + 1.5}, so a single reboot event does NOT immediately trigger a flag
 * on a perfectly stable gateway.
 *
 * <h3>Decision</h3>
 *
 * <p>PRIMARY — direct indicator of unstable gateway firmware or hardware.
 */
public final class F02FlaggedRebootHoursComputer implements FeatureComputer {

    @Override
    public void compute(
            GatewayFeatures.Builder builder,
            List<TelemetryRow> scoringRows,
            List<TelemetryRow> baselineRows,
            BaselineStats offlineStats,
            BaselineStats rebootStats) {

        double threshold = rebootStats.threshold(BaselineStatsComputer.SIGMA_MIN_REBOOT_CNT);

        int flagged = 0;
        for (TelemetryRow row : scoringRows) {
            if (row.rebootCnt() > threshold) {
                flagged++;
            }
        }

        builder.f02FlaggedRebootHours(flagged);
    }
}
