package com.lpdg.sentinel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PredictionWeek} temporal window derivation.
 *
 * <p>These tests validate the Phase 3 temporal-integrity contract: all four window
 * boundaries must be derived correctly from a single Monday anchor date, and no date on
 * or after {@code targetMonday} may fall inside any window.
 */
class PredictionWeekTest {

    // Reference Monday used across multiple tests
    private static final LocalDate MONDAY_2026_03_09 = LocalDate.of(2026, 3, 9);

    @Test
    void constructor_acceptsValidMonday() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.targetMonday()).isEqualTo(MONDAY_2026_03_09);
    }

    @Test
    void of_parsesIsoString() {
        PredictionWeek week = PredictionWeek.of("2026-03-09");
        assertThat(week.targetMonday()).isEqualTo(MONDAY_2026_03_09);
    }

    @Test
    void constructor_rejectsTuesday() {
        assertThatThrownBy(() -> new PredictionWeek(LocalDate.of(2026, 3, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monday");
    }

    @Test
    void constructor_rejectsSunday() {
        assertThatThrownBy(() -> new PredictionWeek(LocalDate.of(2026, 3, 8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monday");
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new PredictionWeek(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Baseline window [T-28d, T) ────────────────────────────

    @Test
    void baselineStart_is28DaysBeforeMonday() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.baselineStart()).isEqualTo(LocalDate.of(2026, 2, 9));
    }

    @Test
    void baselineEnd_isTargetMonday() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.baselineEnd()).isEqualTo(MONDAY_2026_03_09);
    }

    @Test
    void baselineWindow_spans28Days() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        long days = java.time.temporal.ChronoUnit.DAYS.between(week.baselineStart(), week.baselineEnd());
        assertThat(days).isEqualTo(28);
    }

    // ── Scoring window [T-7d, T) ──────────────────────────────

    @Test
    void scoringStart_is7DaysBeforeMonday() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.scoringStart()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void scoringEnd_isTargetMonday() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.scoringEnd()).isEqualTo(MONDAY_2026_03_09);
    }

    @Test
    void scoringWindow_spans7Days() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        long days = java.time.temporal.ChronoUnit.DAYS.between(week.scoringStart(), week.scoringEnd());
        assertThat(days).isEqualTo(7);
    }

    // ── Scoring window is INSIDE baseline window ───────────────

    @Test
    void scoringWindow_isContainedInsideBaselineWindow() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.scoringStart()).isAfterOrEqualTo(week.baselineStart());
        assertThat(week.scoringEnd()).isBeforeOrEqualTo(week.baselineEnd());
    }

    // ── No future data leakage ────────────────────────────────

    @Test
    void targetMonday_isNotInBaselineWindow() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        // baselineEnd is exclusive — targetMonday must NOT be inside
        assertThat(week.isInBaselineWindow(MONDAY_2026_03_09)).isFalse();
    }

    @Test
    void targetMonday_isNotInScoringWindow() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        assertThat(week.isInScoringWindow(MONDAY_2026_03_09)).isFalse();
    }

    @Test
    void dayAfterTargetMonday_isNotInEitherWindow() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        LocalDate future = MONDAY_2026_03_09.plusDays(1);
        assertThat(week.isInBaselineWindow(future)).isFalse();
        assertThat(week.isInScoringWindow(future)).isFalse();
    }

    @Test
    void dayBeforeTargetMonday_isInBothWindows() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        LocalDate lastDay = MONDAY_2026_03_09.minusDays(1); // Sunday
        assertThat(week.isInBaselineWindow(lastDay)).isTrue();
        assertThat(week.isInScoringWindow(lastDay)).isTrue();
    }

    @Test
    void baselineStartDate_isInBaselineWindow_notInScoringWindow() {
        PredictionWeek week = new PredictionWeek(MONDAY_2026_03_09);
        LocalDate start = week.baselineStart();
        assertThat(week.isInBaselineWindow(start)).isTrue();
        assertThat(week.isInScoringWindow(start)).isFalse();
    }
}
