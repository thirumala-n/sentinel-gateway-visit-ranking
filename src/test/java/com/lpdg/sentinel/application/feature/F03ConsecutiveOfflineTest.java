package com.lpdg.sentinel.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for F03 — Maximum Consecutive Offline Hours.
 *
 * <p>Critical regression test: a gap in timestamps (missing hour) MUST break the
 * consecutive run. Two rows at 10:00 and 12:00 with no 11:00 are NOT consecutive.
 *
 * <p>This test class calls {@link F03MaxConsecutiveOfflineComputer#computeMaxRun(List)}
 * directly to isolate the algorithm from the feature builder.
 */
class F03ConsecutiveOfflineTest {

    private static final GatewayId GW_ID = new GatewayId("0639EA5602C1");
    private static final Instant BASE = Instant.parse("2026-03-02T10:00:00Z");
    private static final double THRESHOLD = F03MaxConsecutiveOfflineComputer.NEAR_TOTAL_OUTAGE_THRESHOLD_SEC;

    // ── Basic run detection ───────────────────────────────────

    @Test
    void emptyRows_returnsZero() {
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(List.of())).isEqualTo(0);
    }

    @Test
    void nullRows_returnsZero() {
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(null)).isEqualTo(0);
    }

    @Test
    void singleQualifyingHour_returnsOne() {
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(row(THRESHOLD))).isEqualTo(1);
    }

    @Test
    void singleNonQualifyingHour_returnsZero() {
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(row(0.0))).isEqualTo(0);
    }

    @Test
    void threeConsecutiveQualifyingHours_returnsThree() {
        // 10:00, 11:00, 12:00 — all qualifying and truly adjacent
        List<TelemetryRow> rows = consecutiveRows(3600.0, 3600.0, 3600.0);
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(3);
    }

    @Test
    void allZeros_returnsZero() {
        List<TelemetryRow> rows = consecutiveRows(0.0, 0.0, 0.0, 0.0);
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(0);
    }

    // ── CRITICAL: Gap breaks the run ─────────────────────────

    /**
     * Regression test: 10:00 (qualifying), gap, 12:00 (qualifying).
     *
     * <p>The missing 11:00 hour MUST break the run. The maximum consecutive run is 1,
     * NOT 2.
     */
    @Test
    void missingHour_breaksConsecutiveRun() {
        // Row at 10:00 (offset 0h), then skip to 12:00 (offset 2h) — no 11:00
        Instant t10 = BASE;                           // 10:00
        Instant t12 = BASE.plusSeconds(2 * 3600L);   // 12:00 (gap at 11:00)

        List<TelemetryRow> rows = List.of(
                new TelemetryRow(GW_ID, t10, 3600.0, 0.0, 0),
                new TelemetryRow(GW_ID, t12, 3600.0, 0.0, 0));

        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(1);
    }

    /**
     * Regression test: 5 consecutive hours, 2-hour gap, then 3 more consecutive hours.
     *
     * <p>Expected: max run = 5, not 8.
     */
    @Test
    void gapSplitsLongerRun_returnsLongerSegment() {
        List<TelemetryRow> rows = new ArrayList<>();
        // 5 consecutive qualifying hours
        for (int i = 0; i < 5; i++) {
            rows.add(new TelemetryRow(GW_ID, BASE.plusSeconds((long) i * 3600), 3600.0, 0.0, 0));
        }
        // Gap: skip 5:00 and 6:00
        // 3 more consecutive qualifying hours starting at offset 7h
        for (int i = 7; i < 10; i++) {
            rows.add(new TelemetryRow(GW_ID, BASE.plusSeconds((long) i * 3600), 3600.0, 0.0, 0));
        }

        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(5);
    }

    @Test
    void nonQualifyingHourBreaksRun() {
        // 3 qualifying, 1 non-qualifying (100 sec < 3000), 2 qualifying
        // Max run = 3
        List<TelemetryRow> rows = List.of(
                new TelemetryRow(GW_ID, BASE.plusSeconds(0), 3600.0, 0.0, 0),
                new TelemetryRow(GW_ID, BASE.plusSeconds(3600), 3600.0, 0.0, 0),
                new TelemetryRow(GW_ID, BASE.plusSeconds(7200), 3600.0, 0.0, 0),
                new TelemetryRow(GW_ID, BASE.plusSeconds(10800), 100.0, 0.0, 0),  // breaks run
                new TelemetryRow(GW_ID, BASE.plusSeconds(14400), 3600.0, 0.0, 0),
                new TelemetryRow(GW_ID, BASE.plusSeconds(18000), 3600.0, 0.0, 0));

        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(3);
    }

    @Test
    void exactlyAtThreshold_qualifies() {
        // offline_duration_sec >= 3000 qualifies (>= not >)
        List<TelemetryRow> rows = consecutiveRows(3000.0, 3000.0);
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(2);
    }

    @Test
    void justBelowThreshold_doesNotQualify() {
        List<TelemetryRow> rows = consecutiveRows(2999.9);
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(0);
    }

    @Test
    void fullWeek168ConsecutiveHours_dead() {
        List<TelemetryRow> rows = new ArrayList<>();
        for (int i = 0; i < 168; i++) {
            rows.add(new TelemetryRow(GW_ID, BASE.plusSeconds((long) i * 3600), 3600.0, 0.0, 0));
        }
        assertThat(F03MaxConsecutiveOfflineComputer.computeMaxRun(rows)).isEqualTo(168);
    }

    // ── Helpers ───────────────────────────────────────────────

    private List<TelemetryRow> row(double offlineSec) {
        return List.of(new TelemetryRow(GW_ID, BASE, offlineSec, 0.0, 0));
    }

    /** Creates rows with exactly 1 hour between each timestamp (truly consecutive). */
    private List<TelemetryRow> consecutiveRows(double... offlineValues) {
        List<TelemetryRow> rows = new ArrayList<>();
        for (int i = 0; i < offlineValues.length; i++) {
            Instant ts = BASE.plusSeconds((long) i * 3600);
            rows.add(new TelemetryRow(GW_ID, ts, offlineValues[i], 0.0, 0));
        }
        return rows;
    }
}
