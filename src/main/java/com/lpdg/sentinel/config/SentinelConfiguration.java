package com.lpdg.sentinel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of {@link SentinelProperties} from configuration sources.
 */
@Configuration
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelConfiguration {
}
