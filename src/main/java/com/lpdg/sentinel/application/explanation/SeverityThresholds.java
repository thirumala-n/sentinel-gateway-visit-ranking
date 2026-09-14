package com.lpdg.sentinel.application.explanation;

/**
 * Calibrated threshold constants defining severity boundaries for telemetry features.
 *
 * <p>These values align directly with the normalization factors, saturation points,
 * and empirical distributions established in {@code RiskBasedRankingStrategy} and
 * Phase 2 feature evidence.
 */
public final class SeverityThresholds {

    private SeverityThresholds() {}

    // F01: Flagged Offline Hours (normalized to 84h in scoring)
    public static final int F01_CRITICAL = 42;
    public static final int F01_HIGH = 20;
    public static final int F01_MODERATE = 8;
    public static final int F01_LOW = 1;

    // F02: Flagged Reboot Hours (normalized to 24h in scoring)
    public static final int F02_CRITICAL = 12;
    public static final int F02_HIGH = 8;
    public static final int F02_MODERATE = 4;
    public static final int F02_LOW = 1;

    // F03: Max Consecutive Offline Hours (normalized to 48h in scoring)
    public static final int F03_CRITICAL = 24;
    public static final int F03_HIGH = 12;
    public static final int F03_MODERATE = 6;
    public static final int F03_LOW = 1;

    // F04: Trend Trajectory Ratio (1.2x deteriorating, 0.5x recovering)
    public static final double F04_ACCELERATING = 2.5;
    public static final double F04_DETERIORATING = 1.2;
    public static final double F04_RECOVERING = 0.5;

    // F05: Meter Exposure (fleet median is 156)
    public static final int F05_CRITICAL = 400;
    public static final int F05_HIGH = 200;
    public static final int F05_MODERATE = 80;

    // Technical Risk boundaries
    public static final double TECH_RISK_CRITICAL = 0.50;
    public static final double TECH_RISK_HIGH = 0.35;
    public static final double TECH_RISK_MODERATE = 0.15;
    public static final double TECH_RISK_ACTIONABLE = 0.05;

    // Pairwise score proximity threshold
    public static final double SCORE_PROXIMITY_THRESHOLD = 5.0;
}
