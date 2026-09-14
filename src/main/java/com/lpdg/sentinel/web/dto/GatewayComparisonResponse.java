package com.lpdg.sentinel.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * REST API response providing pairwise comparative justification between two gateways.
 */
public record GatewayComparisonResponse(
        @JsonProperty("higher_gateway_id") String higherGatewayId,
        @JsonProperty("lower_gateway_id") String lowerGatewayId,
        @JsonProperty("reasons") List<String> reasons,
        @JsonProperty("score_proximity_warning") boolean scoreProximityWarning) {

    public GatewayComparisonResponse {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
