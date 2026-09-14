package com.lpdg.sentinel.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * REST API response providing structured, traceable decision evidence for a single gateway.
 */
public record GatewayExplanationResponse(
        @JsonProperty("gateway_id") String gatewayId,
        @JsonProperty("rank") int rank,
        @JsonProperty("score") double score,
        @JsonProperty("decision_category") String decisionCategory,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("confidence") String confidence,
        @JsonProperty("fallback") boolean fallback,
        @JsonProperty("primary_reason") String primaryReason,
        @JsonProperty("summary") String summary,
        @JsonProperty("feature_contributions") List<FeatureContributionDto> featureContributions,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("limitations") List<String> limitations) {

    public GatewayExplanationResponse {
        featureContributions = featureContributions == null ? List.of() : List.copyOf(featureContributions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
