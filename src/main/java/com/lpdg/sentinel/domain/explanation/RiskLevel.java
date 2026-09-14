package com.lpdg.sentinel.domain.explanation;

/**
 * Severity level of technical risk observed on the gateway asset.
 *
 * <p>Levels are calibrated against saturation thresholds in the technical
 * risk scoring model:
 * <ul>
 *   <li>{@link #CRITICAL}: Sustained continuous outage (&ge; 24h) or total telemetry loss.</li>
 *   <li>{@link #HIGH}: Substantial offline anomaly volume (&ge; 20h) or technical risk &ge; 0.35.</li>
 *   <li>{@link #MODERATE}: Notable offline anomaly volume (&ge; 8h) or technical risk &ge; 0.15.</li>
 *   <li>{@link #LOW}: Minor detectable distress (technical risk &ge; 0.05).</li>
 *   <li>{@link #NONE}: Negligible technical distress (technical risk &lt; 0.05, fallback candidate).</li>
 * </ul>
 */
public enum RiskLevel {
    CRITICAL,
    HIGH,
    MODERATE,
    LOW,
    NONE
}
