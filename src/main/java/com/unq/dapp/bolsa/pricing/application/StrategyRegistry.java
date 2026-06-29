package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.pricing.domain.PricingStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry de estrategias de pricing.
 * Permite obtener estrategias por nombre.
 */
@Component
public class StrategyRegistry {

    private final Map<String, PricingStrategy> strategies = new HashMap<>();
    private PricingStrategy defaultStrategy;

    public void register(PricingStrategy strategy) {
        strategies.put(strategy.name(), strategy);
    }

    public void setDefaultStrategy(PricingStrategy strategy) {
        this.defaultStrategy = strategy;
        register(strategy);
    }

    public Optional<PricingStrategy> get(String name) {
        return Optional.ofNullable(strategies.get(name));
    }

    public PricingStrategy getDefault() {
        if (defaultStrategy == null) {
            throw new IllegalStateException("No default pricing strategy configured");
        }
        return defaultStrategy;
    }

    public boolean exists(String name) {
        return strategies.containsKey(name);
    }
}

