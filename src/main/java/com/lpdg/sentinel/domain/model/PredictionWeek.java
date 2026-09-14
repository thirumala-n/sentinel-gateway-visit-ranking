package com.lpdg.sentinel.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable value object representing a prediction week anchored to a target Monday.
 *
 * <p>All temporal windows used by the feature extraction engine are derived from a single
 * {@code targetMonday} date. This is the single source of truth for all window arithmetic
 * and enforces the zero-future-leakage invariant: no observation with timestamp &ge;
 * {@code targetMonday} may influence any feature.
 *
 * <h3>Window Definitions</h3>
 *
 * <pre>
 *   Baseline window W_B : [targetMonday - 28 days,  targetMonday)   (inclusive start, exclusive end)
 *   Scoring  window W_S : [targetMonday -  7 days,  targetMonday)   (inclusive start, exclusive end)
 * </pre>
 *
 * <p>The scoring window W_S is the tail end of the baseline window W_B. It is NOT excluded
 * from the 28-day baseline statistics (mu, sigma). Both windows terminate strictly at
 * {@code targetMonday} — observations on or after that date are never loaded.
 */
public record PredictionWeek(LocalDate targetMonday) {

    /** Number of days in the baseline history window. */
    public static final int BASELINE_DAYS = 28;

    /** Number of days in the recent scoring window. */
    public static final int SCORING_DAYS = 7;

    public PredictionWeek {
        Objects.requireNonNull(targetMonday, "targetMonday must not be null");
        if (targetMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "targetMonday must be a Monday, but was: "
                            + targetMonday
                            + " ("
                            + targetMonday.getDayOfWeek()
                            + ")");
        }
    }

    /**
     * Creates a {@link PredictionWeek} from a Monday date string in ISO-8601 format (yyyy-MM-dd).
     *
     * @param isoDate ISO-8601 date string representing the target Monday
     * @return constructed {@link PredictionWeek}
     */
    public static PredictionWeek of(String isoDate) {
        return new PredictionWeek(LocalDate.parse(isoDate));
    }

    // ── Baseline window [T-28d, T) ──────────────────────────────

    /**
     * Inclusive start of the 28-day baseline window.
     *
     * @return {@code targetMonday - 28 days}
     */
    public LocalDate baselineStart() {
        return targetMonday.minusDays(BASELINE_DAYS);
    }

    /**
     * Exclusive end of the 28-day baseline window (equals {@code targetMonday}).
     *
     * @return {@code targetMonday}
     */
    public LocalDate baselineEnd() {
        return targetMonday;
    }

    // ── Scoring window [T-7d, T) ────────────────────────────────

    /**
     * Inclusive start of the 7-day scoring window.
     *
     * @return {@code targetMonday - 7 days}
     */
    public LocalDate scoringStart() {
        return targetMonday.minusDays(SCORING_DAYS);
    }

    /**
     * Exclusive end of the 7-day scoring window (equals {@code targetMonday}).
     *
     * @return {@code targetMonday}
     */
    public LocalDate scoringEnd() {
        return targetMonday;
    }

    // ── Trend sub-windows [T-168h, T) split at T-72h ──────────

    /**
     * Start of the "recent" sub-window used by the trend trajectory feature (F04).
     *
     * <p>Formula: last 72 hours before {@code targetMonday}: {@code [targetMonday - 3 days, targetMonday)}.
     *
     * @return {@code targetMonday - 3 days}
     */
    public LocalDate trendRecentStart() {
        return targetMonday.minusDays(3);
    }

    /**
     * Start of the "preceding" sub-window used by the trend trajectory feature (F04).
     *
     * <p>Formula: hours 168–72 before {@code targetMonday}: {@code [targetMonday - 7 days, targetMonday - 3 days)}.
     *
     * @return {@code targetMonday - 7 days}
     */
    public LocalDate trendPrecedingStart() {
        return targetMonday.minusDays(7);
    }

    /**
     * Returns {@code true} if the given date is strictly within the baseline window
     * (i.e., {@code baselineStart() <= date < baselineEnd()}).
     *
     * @param date date to test
     * @return {@code true} if date falls inside the baseline window
     */
    public boolean isInBaselineWindow(LocalDate date) {
        return !date.isBefore(baselineStart()) && date.isBefore(baselineEnd());
    }

    /**
     * Returns {@code true} if the given date is strictly within the scoring window
     * (i.e., {@code scoringStart() <= date < scoringEnd()}).
     *
     * @param date date to test
     * @return {@code true} if date falls inside the scoring window
     */
    public boolean isInScoringWindow(LocalDate date) {
        return !date.isBefore(scoringStart()) && date.isBefore(scoringEnd());
    }

    @Override
    public String toString() {
        return "PredictionWeek[" + targetMonday + ", baseline=" + baselineStart() + ".." + baselineEnd() + ")";
    }
}
