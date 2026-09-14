package com.lpdg.sentinel.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Consistent structured error format for all REST API error responses.
 */
public record ApiErrorResponse(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("status") int status,
        @JsonProperty("error") String error,
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("path") String path) {}
