package com.unq.dapp.bolsa.config;

import com.unq.dapp.bolsa.pricing.application.StrategyRegistry;
import com.unq.dapp.bolsa.pricing.domain.MatchMetricsStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del módulo de pricing.
 * Registra las estrategias disponibles y establece la estrategia por defecto.
 */
@Configuration
public class PricingConfig {

    @Bean
    public StrategyRegistry strategyRegistry() {
        StrategyRegistry registry = new StrategyRegistry();

        // Registrar MatchMetricsStrategy como estrategia por defecto
        MatchMetricsStrategy matchMetricsStrategy = new MatchMetricsStrategy();
        registry.setDefaultStrategy(matchMetricsStrategy);

        return registry;
    }
}

