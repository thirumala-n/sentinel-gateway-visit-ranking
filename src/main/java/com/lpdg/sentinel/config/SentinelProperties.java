package com.lpdg.sentinel.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the Sentinel application.
 *
 * <p>Bound from {@code sentinel.*} keys in {@code application.yml}.
 * All values are externalisable via environment variables.</p>
 */
@Validated
@ConfigurationProperties(prefix = "sentinel")
public record SentinelProperties(

        @NotBlank String dataDir,

        @Min(1) int visitsPerWeek,

        @Min(1) int baselineWindowDays,

        @Min(1) int recentWindowDays,

        @Min(1) int scoredWeeks) {
}
