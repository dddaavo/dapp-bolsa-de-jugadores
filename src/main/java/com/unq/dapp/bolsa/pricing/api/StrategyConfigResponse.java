package com.unq.dapp.bolsa.pricing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unq.dapp.bolsa.pricing.domain.StrategyConfig;
import io.swagger.v3.oas.annotations.media.Schema;

public record StrategyConfigResponse(
        @Schema(description = "Nombre de la estrategia") String name,
        @Schema(description = "¿Está activa?") boolean active,
        @Schema(description = "Versión de configuración (se incrementa con cada actualización de pesos)") int version,
        @Schema(description = "Pesos configurados para la estrategia") Object pesos
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
