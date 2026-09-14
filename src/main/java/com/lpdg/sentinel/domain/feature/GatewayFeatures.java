package com.lpdg.sentinel.domain.feature;

import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.model.Gateway;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.util.Objects;

/**
 * Immutable feature vector for a single gateway for a single prediction week.
 *
 * <p>This is the central Phase 3 output. It contains all approved feature values
 * (F01–F08) and is the sole input to the {@link com.lpdg.sentinel.domain.ranking.RankingStrategy}.
 * No repository objects are held here.
 *
 * <h3>Feature Definitions</h3>
 *
 * <ul>
 *   <li>F01 — Flagged Offline Hours: hours in scoring window where offline_duration_sec exceeds
 *       3-sigma threshold. Sigma floor: 60.0 seconds. [PRIMARY]
 *   <li>F02 — Flagged Reboot Hours: hours in scoring window where reboot_cnt exceeds 3-sigma
 *       threshold. Sigma floor: 0.5. [PRIMARY]
 *   <li>F03 — Max Consecutive Offline Hours: longest unbroken run of hours with
 *       offline_duration_sec &ge; 3000 seconds. A gap in timestamps (missing hour) breaks the
 *       run. [PRIMARY]
 *   <li>F04 — Outage Trajectory Ratio: SUM(last_72h) / (SUM(prev_96h) * (72/96) + 3600).
 *       Clamped [0, 5]. [PRIMARY]
 *   <li>F05 — Installed Meter Count: n_meters_installed from gateway_master.csv. Fleet median
 *       (156) if missing. [PRIMARY]
 *   <li>F06 — Cumulative Excess Z-Score: SUM(MAX(0, (X - mu) / sigma_eff - 3.0)) over scoring
 *       window. Hourly excess capped at 10.0. [SECONDARY]
 *   <li>F07 — Historic Meter Read Success Rate: 26-week average percentage. Fleet median 91.0%
 *       if missing. [EXPLANATION]
 *   <li>F08 — Disconnection Frequency: SUM(disconnection_cnt) over scoring window. Highly
 *       collinear with F01 (r=0.94). [EXPLANATION]
 * </ul>
 */
public record GatewayFeatures(
        GatewayId gatewayId,
        PredictionWeek week,

        // ── Primary features ───────────────────────────────────
        int f01FlaggedOfflineHours,
        int f02FlaggedRebootHours,
        int f03MaxConsecutiveOfflineHours,
        double f04TrendTrajectoryRatio,
        int f05MetersExposure,

        // ── Secondary features ─────────────────────────────────
        double f06CumulativeExcessZ,

        // ── Explanation features ───────────────────────────────
        double f07HistoricReadSuccess,
        int f08DisconnectionFrequency,

        // ── Data quality ───────────────────────────────────────
        DataConfidence dataConfidence) {

    public GatewayFeatures {
        Objects.requireNonNull(gatewayId, "gatewayId must not be null");
        Objects.requireNonNull(week, "week must not be null");
        Objects.requireNonNull(dataConfidence, "dataConfidence must not be null");
        if (f01FlaggedOfflineHours < 0) {
            throw new IllegalArgumentException("f01FlaggedOfflineHours must be >= 0");
        }
        if (f02FlaggedRebootHours < 0) {
            throw new IllegalArgumentException("f02FlaggedRebootHours must be >= 0");
        }
        if (f03MaxConsecutiveOfflineHours < 0) {
            throw new IllegalArgumentException("f03MaxConsecutiveOfflineHours must be >= 0");
        }
        if (f04TrendTrajectoryRatio < 0.0 || f04TrendTrajectoryRatio > 5.0) {
            throw new IllegalArgumentException(
                    "f04TrendTrajectoryRatio must be in [0.0, 5.0], got: " + f04TrendTrajectoryRatio);
        }
        if (f05MetersExposure < 1) {
            throw new IllegalArgumentException("f05MetersExposure must be >= 1");
        }
        if (f06CumulativeExcessZ < 0.0) {
            throw new IllegalArgumentException("f06CumulativeExcessZ must be >= 0.0");
        }
        if (f07HistoricReadSuccess < 0.0 || f07HistoricReadSuccess > 100.0) {
            throw new IllegalArgumentException(
                    "f07HistoricReadSuccess must be in [0.0, 100.0], got: " + f07HistoricReadSuccess);
        }
        if (f08DisconnectionFrequency < 0) {
            throw new IllegalArgumentException("f08DisconnectionFrequency must be >= 0");
        }
    }

    /** Returns a new {@link Builder} for constructing {@link GatewayFeatures}. */
    public static Builder builder(GatewayId gatewayId, PredictionWeek week) {
        return new Builder(gatewayId, week);
    }

    /**
     * Mutable builder for {@link GatewayFeatures}.
     *
     * <p>Defaults are set to safe zero values. {@link #dataConfidence} defaults to
     * {@link DataConfidence#FULL} and must be explicitly overridden for cold-start or
     * missing-telemetry gateways.
     */
    public static final class Builder {
        private final GatewayId gatewayId;
        private final PredictionWeek week;
        private int f01FlaggedOfflineHours = 0;
        private int f02FlaggedRebootHours = 0;
        private int f03MaxConsecutiveOfflineHours = 0;
        private double f04TrendTrajectoryRatio = 1.0;
        private int f05MetersExposure = Gateway.DEFAULT_METERS;
        private double f06CumulativeExcessZ = 0.0;
        private double f07HistoricReadSuccess = 91.0;
        private int f08DisconnectionFrequency = 0;
        private DataConfidence dataConfidence = DataConfidence.FULL;

        private Builder(GatewayId gatewayId, PredictionWeek week) {
            this.gatewayId = Objects.requireNonNull(gatewayId);
            this.week = Objects.requireNonNull(week);
        }

        public Builder f01FlaggedOfflineHours(int v) {
            this.f01FlaggedOfflineHours = v;
            return this;
        }

        public Builder f02FlaggedRebootHours(int v) {
            this.f02FlaggedRebootHours = v;
            return this;
        }

        public Builder f03MaxConsecutiveOfflineHours(int v) {
            this.f03MaxConsecutiveOfflineHours = v;
            return this;
        }

        public Builder f04TrendTrajectoryRatio(double v) {
            this.f04TrendTrajectoryRatio = v;
            return this;
        }

        public Builder f05MetersExposure(int v) {
            this.f05MetersExposure = v;
            return this;
        }

        public Builder f06CumulativeExcessZ(double v) {
            this.f06CumulativeExcessZ = v;
            return this;
        }

        public Builder f07HistoricReadSuccess(double v) {
            this.f07HistoricReadSuccess = v;
            return this;
        }

        public Builder f08DisconnectionFrequency(int v) {
            this.f08DisconnectionFrequency = v;
            return this;
        }

        public Builder dataConfidence(DataConfidence v) {
            this.dataConfidence = Objects.requireNonNull(v);
            return this;
        }

        /** Builds the immutable {@link GatewayFeatures} record. */
        public GatewayFeatures build() {
            return new GatewayFeatures(
                    gatewayId,
                    week,
                    f01FlaggedOfflineHours,
                    f02FlaggedRebootHours,
                    f03MaxConsecutiveOfflineHours,
                    f04TrendTrajectoryRatio,
                    f05MetersExposure,
                    f06CumulativeExcessZ,
                    f07HistoricReadSuccess,
                    f08DisconnectionFrequency,
                    dataConfidence);
        }
    }
}
