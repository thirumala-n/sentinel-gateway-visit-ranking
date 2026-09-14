package com.lpdg.sentinel.config;

import com.lpdg.sentinel.application.explanation.DecisionExplanationService;
import com.lpdg.sentinel.domain.ranking.BaselineRankingStrategy;
import com.lpdg.sentinel.domain.ranking.RankingStrategy;
import com.lpdg.sentinel.domain.ranking.RiskBasedRankingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing bean definitions for ranking strategies,
 * explanation services, and application properties binding.
 */
@Configuration
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelConfiguration {

    /**
     * Provides the baseline ranking strategy when explicitly configured via
     * {@code sentinel.ranking-strategy=baseline}.
     *
     * @return baseline strategy instance
     */
    @Bean
    @ConditionalOnProperty(name = "sentinel.ranking-strategy", havingValue = "baseline")
    public RankingStrategy baselineRankingStrategy() {
        return new BaselineRankingStrategy();
    }

    /**
     * Provides the default risk-based ranking strategy calibrated against historical data.
     *
     * @return risk-based strategy instance
     */
    @Bean
    @ConditionalOnMissingBean(RankingStrategy.class)
    public RankingStrategy riskBasedRankingStrategy() {
        return new RiskBasedRankingStrategy();
    }

    /**
     * Provides the stateless decision explanation service.
     *
     * @return decision explanation service instance
     */
    @Bean
    public DecisionExplanationService decisionExplanationService() {
        return new DecisionExplanationService();
    }
}
