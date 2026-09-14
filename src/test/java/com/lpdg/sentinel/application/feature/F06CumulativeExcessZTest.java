package com.lpdg.sentinel.application.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for F06 — Cumulative Excess Z-Score.
 *
 * <p>Validates:
 * <ul>
 *   <li>Stable gateway: 0.0 (no excess above threshold)
 *   <li>Single anomalous hour: exact excess value
 *   <li>Multiple anomalous hours: summed excess
 *   <li>Hourly excess capped at 10.0
 *   <li>Sigma floor prevents NaN / infinity
 * </ul>
 */
class F06CumulativeExcessZTest {

    private static final GatewayId GW_ID = new GatewayId("0639EA5602C1");
    private static final PredictionWeek WEEK = PredictionWeek.of("2026-03-09");
    private static final Instant BASE = Instant.parse("2026-03-02T00:00:00Z");

    private final F06CumulativeExcessZComputer f06 = new F06CumulativeExcessZComputer();

    @Test
    void stableGateway_returnsZero() {
        // All hours at mu=0; sigmaEff=60 → threshold=180; all below → 0 excess
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);
        List<TelemetryRow> scoring = rows(10.0, 50.0, 100.0);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f06CumulativeExcessZ()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void singleAnomaly_exactExcess() {
        // mu=0, sigma=0, sigmaEff=60 → threshold=3-sigma at 180
        // X=3600 → z=(3600-0)/60=60 → excess=60-3=57 (capped at 10) → 10.0
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);
        List<TelemetryRow> scoring = rows(3600.0);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        // Excess = 57 but capped at 10.0
        assertThat(builder.build().f06CumulativeExcessZ()).isCloseTo(10.0, within(0.001));
    }

    @Test
    void hourlyExcessCappedAt10() {
        // Even extreme values should not exceed 10.0 per hour
        // mu=0, sigma=0, sigmaEff=60: X=1_000_000 → z=16666 → excess capped at 10
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);
        List<TelemetryRow> scoring = rows(1_000_000.0);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f06CumulativeExcessZ()).isCloseTo(10.0, within(0.001));
    }

    @Test
    void multipleAnomalousHours_summedExcess() {
        // mu=0, sigma=120, sigmaEff=120 (>60) → threshold=0+3*120=360
        // Hour1: 600 → z=5, excess=2.0
        // Hour2: 840 → z=7, excess=4.0
        // Sum = 6.0
        BaselineStats offlineStats = new BaselineStats(0.0, 120.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);
        List<TelemetryRow> scoring = rows(600.0, 840.0);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f06CumulativeExcessZ()).isCloseTo(6.0, within(0.001));
    }

    @Test
    void belowThresholdHours_contributeZero() {
        // mu=0, sigma=120 → threshold=360; X=200 → z=1.67, excess=MAX(0, 1.67-3)=0
        BaselineStats offlineStats = new BaselineStats(0.0, 120.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);
        List<TelemetryRow> scoring = rows(200.0, 100.0, 350.0);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f06CumulativeExcessZ()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void emptyWindow_returnsZero() {
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 0, true);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 0, true);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, List.of(), List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f06CumulativeExcessZ()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void sigmaFloor_preventsNanOrInfinity() {
        // sigma=0 → sigmaEff=60; no NaN or Infinity in output
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);
        List<TelemetryRow> scoring = rows(3600.0, 3600.0, 3600.0);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f06.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        double result = builder.build().f06CumulativeExcessZ();
        assertThat(Double.isNaN(result)).isFalse();
        assertThat(Double.isInfinite(result)).isFalse();
        assertThat(result).isGreaterThanOrEqualTo(0.0);
    }

    // ── Helper ────────────────────────────────────────────────

    private List<TelemetryRow> rows(double... offlineValues) {
        List<TelemetryRow> result = new ArrayList<>();
        for (int i = 0; i < offlineValues.length; i++) {
            Instant ts = BASE.plusSeconds((long) i * 3600);
            result.add(new TelemetryRow(GW_ID, ts, offlineValues[i], 0.0, 0));
        }
        return result;
    }
}
