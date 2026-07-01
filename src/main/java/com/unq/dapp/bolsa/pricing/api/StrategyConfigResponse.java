package com.unq.dapp.bolsa.pricing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unq.dapp.bolsa.pricing.domain.StrategyConfig;

public record StrategyConfigResponse(
        String name,
        boolean active,
        int version,
        Object pesos
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static StrategyConfigResponse from(StrategyConfig config) {
        Object parsedWeights;
        try {
            parsedWeights = MAPPER.readValue(config.getWeightsJson(), Object.class);
        } catch (Exception e) {
            parsedWeights = config.getWeightsJson();
        }
        return new StrategyConfigResponse(
                config.getName(),
                config.isActive(),
                config.getConfigVersion(),
                parsedWeights
        );
    }
}
