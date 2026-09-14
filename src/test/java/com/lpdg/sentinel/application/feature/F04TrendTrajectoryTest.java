package com.lpdg.sentinel.application.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for F04 — Outage Trajectory Ratio.
 *
 * <p>Validates:
 * <ul>
 *   <li>Stable gateway (ratio ≈ 1.0)
 *   <li>Rapidly deteriorating gateway (ratio → 5.0, clamped)
 *   <li>Recovering gateway (ratio < 1.0)
 *   <li>Zero offline denominator → Laplace smoothing prevents zero-division
 *   <li>Insufficient data (< 24 rows) → neutral ratio 1.0
 *   <li>Ratio clamped at MAX_RATIO=5.0
 * </ul>
 */
class F04TrendTrajectoryTest {

    private static final GatewayId GW_ID = new GatewayId("0639EA5602C1");
    private static final Instant BASE = Instant.parse("2026-03-02T00:00:00Z"); // scoring start

    // ── Insufficient data ─────────────────────────────────────

    @Test
    void insufficientRows_returnsNeutralRatio() {
        List<TelemetryRow> rows = rows(10, 1000.0);
        assertThat(F04TrendTrajectoryComputer.computeRatio(rows))
                .isCloseTo(F04TrendTrajectoryComputer.NEUTRAL_RATIO, within(0.001));
    }

    @Test
    void emptyRows_returnsNeutralRatio() {
        assertThat(F04TrendTrajectoryComputer.computeRatio(List.of()))
                .isCloseTo(F04TrendTrajectoryComputer.NEUTRAL_RATIO, within(0.001));
    }

    @Test
    void nullRows_returnsNeutralRatio() {
        assertThat(F04TrendTrajectoryComputer.computeRatio(null))
                .isCloseTo(F04TrendTrajectoryComputer.NEUTRAL_RATIO, within(0.001));
    }

    // ── Zero denominator — Laplace smoothing ──────────────────

    @Test
    void zeroPreceding96h_laplacePreventsZeroDivision() {
        // 168 rows: first 96h all zero offline, last 72h = 3600/hr
        // SUM(prev96) = 0 → denominator = 0*(72/96) + 3600 = 3600
        // SUM(last72) = 72 * 3600 = 259200
        // ratio = 259200 / 3600 = 72 → clamped to 5.0
        List<TelemetryRow> rows = buildTrendRows(/*prev96OfflineSec*/ 0.0, /*last72OfflineSec*/ 3600.0);
        double ratio = F04TrendTrajectoryComputer.computeRatio(rows);
        assertThat(ratio).isCloseTo(F04TrendTrajectoryComputer.MAX_RATIO, within(0.001));
    }

    // ── Stable gateway ────────────────────────────────────────

    @Test
    void stableGateway_ratioApproximatelyOne() {
        // Equal offline in both sub-windows (same per-hour rate)
        // prev96: 96 hrs × 1800 sec = 172800; last72: 72 hrs × 1800 = 129600
        // denominator = 172800 * (72/96) + 3600 = 129600 + 3600 = 133200
        // ratio = 129600 / 133200 ≈ 0.973 (close to 1)
        List<TelemetryRow> rows = buildTrendRows(1800.0, 1800.0);
        double ratio = F04TrendTrajectoryComputer.computeRatio(rows);
        assertThat(ratio).isBetween(0.95, 1.05);
    }

    // ── Deteriorating gateway ─────────────────────────────────

    @Test
    void deterioratingGateway_ratioGreaterThanOne() {
        // Prev96h: low offline (200/hr); last72h: high offline (3600/hr)
        // SUM(prev96) = 96 * 200 = 19200; SUM(last72) = 72 * 3600 = 259200
        // denominator = 19200 * (72/96) + 3600 = 14400 + 3600 = 18000
        // ratio = 259200 / 18000 = 14.4 → clamped to 5.0
        List<TelemetryRow> rows = buildTrendRows(200.0, 3600.0);
        double ratio = F04TrendTrajectoryComputer.computeRatio(rows);
        assertThat(ratio).isCloseTo(F04TrendTrajectoryComputer.MAX_RATIO, within(0.001));
    }

    // ── Recovering gateway ────────────────────────────────────

    @Test
    void recoveringGateway_ratioBelowOne() {
        // Prev96h: heavy offline (3600/hr); last72h: mostly recovered (100/hr)
        // SUM(prev96) = 96 * 3600 = 345600; SUM(last72) = 72 * 100 = 7200
        // denominator = 345600 * (72/96) + 3600 = 259200 + 3600 = 262800
        // ratio = 7200 / 262800 ≈ 0.027
        List<TelemetryRow> rows = buildTrendRows(3600.0, 100.0);
        double ratio = F04TrendTrajectoryComputer.computeRatio(rows);
        assertThat(ratio).isLessThan(1.0);
        assertThat(ratio).isGreaterThanOrEqualTo(0.0);
    }

    // ── Clamping ──────────────────────────────────────────────

    @Test
    void ratio_clampedAtMaxRatio() {
        // Extreme deterioration — ratio should be clamped to 5.0
        List<TelemetryRow> rows = buildTrendRows(0.0, 3600.0);
        double ratio = F04TrendTrajectoryComputer.computeRatio(rows);
        assertThat(ratio).isEqualTo(F04TrendTrajectoryComputer.MAX_RATIO);
    }

    @Test
    void ratio_clampedAtZeroMinimum() {
        // Should never return negative (guaranteed by formula)
        List<TelemetryRow> rows = buildTrendRows(3600.0, 0.0);
        double ratio = F04TrendTrajectoryComputer.computeRatio(rows);
        assertThat(ratio).isGreaterThanOrEqualTo(0.0);
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Builds exactly 168 hourly rows (7 days). The split between "preceding 96h" and
     * "recent 72h" is at hour 96 (i.e., rows 0–95 = prev96, rows 96–167 = last72).
     * The latest row is at BASE + 167h, so the split point = (BASE+167h) - 72h = BASE+95h.
     */
    private List<TelemetryRow> buildTrendRows(double prev96OfflineSec, double last72OfflineSec) {
        List<TelemetryRow> rows = new ArrayList<>();
        for (int i = 0; i < 168; i++) {
            Instant ts = BASE.plusSeconds((long) i * 3600);
            double offline = i < 96 ? prev96OfflineSec : last72OfflineSec;
            rows.add(new TelemetryRow(GW_ID, ts, offline, 0.0, 0));
        }
        return rows;
    }

    private List<TelemetryRow> rows(int count, double offlineSec) {
        List<TelemetryRow> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new TelemetryRow(GW_ID, BASE.plusSeconds((long) i * 3600), offlineSec, 0.0, 0));
        }
        return result;
    }
}
