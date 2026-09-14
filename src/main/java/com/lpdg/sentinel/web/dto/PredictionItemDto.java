package com.lpdg.sentinel.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST API representation of a single recommended gateway visit.
 */
public record PredictionItemDto(
        @JsonProperty("rank") int rank,
        @JsonProperty("gateway_id") String gatewayId,
        @JsonProperty("score") double score,
        @JsonProperty("reason") String reason,
        @JsonProperty("decision_category") String decisionCategory,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("confidence") String confidence,
        @JsonProperty("fallback") boolean fallback) {}
