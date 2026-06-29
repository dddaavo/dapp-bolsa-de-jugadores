package com.unq.dapp.bolsa.config;

import com.unq.dapp.bolsa.pricing.application.StrategyRegistry;
import com.unq.dapp.bolsa.pricing.domain.MatchMetricsStrategy;
import com.unq.dapp.bolsa.pricing.domain.PositionWeightedStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PricingConfig {

    @Bean
    public StrategyRegistry strategyRegistry() {
        StrategyRegistry registry = new StrategyRegistry();

        registry.setDefaultStrategy(new MatchMetricsStrategy());
        registry.register(new PositionWeightedStrategy());

        return registry;
    }
}

