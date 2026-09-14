package com.lpdg.sentinel.domain.explanation;

/**
 * Operational classification of a ranked gateway visit decision.
 *
 * <p>Categorizes the primary decision driver so dispatchers can instantly
 * understand why an asset was selected:
 * <ul>
 *   <li>{@link #MISSING_TELEMETRY}: Gateway produced zero telemetry across the scoring week;
 *       potential complete communications or power outage.</li>
 *   <li>{@link #ACTIONABLE_RISK}: Gateway exhibits strong technical failure symptoms
 *       (technical risk &ge; 0.50) scaled by customer impact.</li>
 *   <li>{@link #HIGH_PRIORITY}: Gateway exhibits verified technical distress and high
 *       customer impact justifying urgent dispatch.</li>
 *   <li>{@link #LOW_CONFIDENCE}: Gateway lacks sufficient historical baseline data
 *       (&lt; 28 days), requiring operator verification.</li>
 *   <li>{@link #FALLBACK}: Gateway displays negligible technical distress; included
 *       solely to satisfy the weekly capacity budget.</li>
 * </ul>
 */
public enum DecisionCategory {
    MISSING_TELEMETRY,
    ACTIONABLE_RISK,
    HIGH_PRIORITY,
    LOW_CONFIDENCE,
    FALLBACK
}
