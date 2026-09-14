package com.lpdg.sentinel.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for F01 (Flagged Offline Hours) and F02 (Flagged Reboot Hours) computers.
 *
 * <p>Validates:
 * <ul>
 *   <li>Normal gateway: correct flag count
 *   <li>Stable gateway: sigma=0, floor applied, no spurious flags
 *   <li>Single anomaly detection
 *   <li>All hours anomalous
 *   <li>Empty scoring window
 *   <li>Exact threshold boundary (strictly greater than)
 * </ul>
 */
class F01F02FlaggedHoursTest {

    private static final GatewayId GW_ID = new GatewayId("0639EA5602C1");
    private static final PredictionWeek WEEK = PredictionWeek.of("2026-03-09");

    private F01FlaggedOfflineHoursComputer f01;
    private F02FlaggedRebootHoursComputer f02;

    @BeforeEach
    void setUp() {
        f01 = new F01FlaggedOfflineHoursComputer();
        f02 = new F02FlaggedRebootHoursComputer();
    }

    // ── F01 — Flagged Offline Hours ───────────────────────────

    @Test
    void f01_normalGateway_countsCorrectFlags() {
        // mu=100, sigma=200 → sigmaEff=200 → threshold=100+3*200=700
        // Hours: 800 (flagged), 600 (not), 900 (flagged), 100 (not), 50 (not)
        BaselineStats offlineStats = new BaselineStats(100.0, 200.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> scoring = rows(800.0, 600.0, 900.0, 100.0, 50.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f01.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f01FlaggedOfflineHours()).isEqualTo(2);
    }

    @Test
    void f01_stableGateway_sigmaZeroFloorPreventsSpuriousFlags() {
        // mu=0, sigma=0 → sigmaEff=60 (floor) → threshold=0+3*60=180
        // A 1-second blip should NOT flag (1 <= 180)
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> scoring = rows(1.0, 5.0, 10.0, 50.0, 100.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f01.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f01FlaggedOfflineHours()).isEqualTo(0);
    }

    @Test
    void f01_singleAnomaly() {
        // mu=0, sigma=0 → threshold=180; one hour at 3600 (total outage)
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> scoring = rows(3600.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f01.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f01FlaggedOfflineHours()).isEqualTo(1);
    }

    @Test
    void f01_emptyWindow_returnsZero() {
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 0, true);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 0, true);

        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f01.compute(builder, List.of(), List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f01FlaggedOfflineHours()).isEqualTo(0);
    }

    @Test
    void f01_exactlyAtThreshold_notFlagged() {
        // Flagging is strictly greater-than: X > threshold
        // mu=0, sigma=0, floor=60 → threshold=180; hour at exactly 180 should NOT flag
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> scoring = rows(180.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f01.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f01FlaggedOfflineHours()).isEqualTo(0);
    }

    @Test
    void f01_oneAboveThreshold_isFlagged() {
        // mu=0, sigma=0, floor=60 → threshold=180; 180.001 should flag
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> scoring = rows(180.001);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f01.compute(builder, scoring, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f01FlaggedOfflineHours()).isEqualTo(1);
    }

    // ── F02 — Flagged Reboot Hours ───────────────────────────

    @Test
    void f02_stableGateway_rebootFloor_preventsOneRebootFromFlagging() {
        // mu=0, sigma=0 → sigmaEff=0.5 (floor) → threshold=0+3*0.5=1.5
        // 1 reboot does NOT exceed 1.5
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> rebootRows = rebootRows(1.0, 0.0, 0.0, 1.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f02.compute(builder, rebootRows, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f02FlaggedRebootHours()).isEqualTo(0);
    }

    @Test
    void f02_twoRebootsInHour_exceedsFloorThreshold() {
        // mu=0, sigma=0 → threshold=1.5; 2 reboots > 1.5 → flagged
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> rebootRows = rebootRows(2.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f02.compute(builder, rebootRows, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f02FlaggedRebootHours()).isEqualTo(1);
    }

    @Test
    void f02_repeatedAnomalies_countsAll() {
        // mu=0, sigma=0, floor=0.5 → threshold=1.5
        // 5 hours each with 3 reboots → all 5 flagged
        BaselineStats offlineStats = new BaselineStats(0.0, 0.0, 672, false);
        BaselineStats rebootStats = new BaselineStats(0.0, 0.0, 672, false);

        List<TelemetryRow> rebootRows = rebootRows(3.0, 3.0, 3.0, 3.0, 3.0);
        GatewayFeatures.Builder builder = GatewayFeatures.builder(GW_ID, WEEK).f05MetersExposure(200);
        f02.compute(builder, rebootRows, List.of(), offlineStats, rebootStats);

        assertThat(builder.build().f02FlaggedRebootHours()).isEqualTo(5);
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Builds scoring rows with only offline_duration_sec set; reboot=0, disconnect=0. */
    private List<TelemetryRow> rows(double... offlineValues) {
        List<TelemetryRow> result = new ArrayList<>();
        Instant base = Instant.parse("2026-03-02T00:00:00Z");
        for (int i = 0; i < offlineValues.length; i++) {
            Instant ts = base.plusSeconds((long) i * 3600);
            result.add(new TelemetryRow(GW_ID, ts, offlineValues[i], 0.0, 0));
        }
        return result;
    }

    /** Builds scoring rows with reboot_cnt set; offline=0, disconnect=0. */
    private List<TelemetryRow> rebootRows(double... rebootValues) {
        List<TelemetryRow> result = new ArrayList<>();
        Instant base = Instant.parse("2026-03-02T00:00:00Z");
        for (int i = 0; i < rebootValues.length; i++) {
            Instant ts = base.plusSeconds((long) i * 3600);
            result.add(new TelemetryRow(GW_ID, ts, 0.0, rebootValues[i], 0));
        }
        return result;
    }
}
