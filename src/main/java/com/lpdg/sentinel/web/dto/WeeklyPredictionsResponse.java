package com.lpdg.sentinel.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * REST API response containing the weekly set of ranked gateway visit recommendations.
 */
public record WeeklyPredictionsResponse(
        @JsonProperty("week_start") String weekStart,
        @JsonProperty("total_selected") int totalSelected,
        @JsonProperty("capacity") int capacity,
        @JsonProperty("ranking_method") String rankingMethod,
        @JsonProperty("generated_at") Instant generatedAt,
        @JsonProperty("predictions") List<PredictionItemDto> predictions) {

    public WeeklyPredictionsResponse {
        predictions = predictions == null ? List.of() : List.copyOf(predictions);
    }
}
