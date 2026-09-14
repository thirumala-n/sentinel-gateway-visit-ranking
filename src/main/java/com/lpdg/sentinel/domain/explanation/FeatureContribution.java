package com.lpdg.sentinel.domain.explanation;

import java.util.Objects;

/**
 * Granular breakdown of a single feature's value, operational severity, and active contribution.
 *
 * @param featureCode identifier code (e.g., "F01", "F02", "F05")
 * @param featureName descriptive feature name (e.g., "Flagged Offline Hours")
 * @param valueFormatted human-readable formatted value with units (e.g., "42h", "186 meters")
 * @param severityLabel categorized severity level (e.g., "CRITICAL", "HIGH", "MODERATE", "NOMINAL")
 * @param active {@code true} if this feature actively contributed to elevating the gateway's priority
 */
public record FeatureContribution(
        String featureCode,
        String featureName,
        String valueFormatted,
        String severityLabel,
        boolean active) {

    public FeatureContribution {
        Objects.requireNonNull(featureCode, "featureCode must not be null");
        Objects.requireNonNull(featureName, "featureName must not be null");
        Objects.requireNonNull(valueFormatted, "valueFormatted must not be null");
        Objects.requireNonNull(severityLabel, "severityLabel must not be null");
    }
}
