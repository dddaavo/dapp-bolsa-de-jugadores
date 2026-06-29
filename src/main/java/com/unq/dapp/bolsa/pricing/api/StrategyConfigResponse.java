package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.pricing.domain.StrategyConfig;
import io.swagger.v3.oas.annotations.media.Schema;

public record StrategyConfigResponse(
        @Schema(description = "Nombre de la estrategia") String name,
        @Schema(description = "¿Está activa?") boolean active,
        @Schema(description = "Versión de configuración (se incrementa con cada actualización de pesos)") int configVersion,
        @Schema(description = "JSON con los pesos configurados para la estrategia") String weightsJson
) {
    public static StrategyConfigResponse from(StrategyConfig config) {
        return new StrategyConfigResponse(
                config.getName(),
                config.isActive(),
                config.getConfigVersion(),
                config.getWeightsJson()
        );
    }
}
