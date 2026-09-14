package com.lpdg.sentinel.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST API representation of a single feature's contribution to the decision.
 */
public record FeatureContributionDto(
        @JsonProperty("feature_code") String featureCode,
        @JsonProperty("feature_name") String featureName,
        @JsonProperty("value") String value,
        @JsonProperty("severity") String severity,
        @JsonProperty("active") boolean active) {}
