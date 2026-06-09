package com.unq.dapp.bolsa.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecalculateRequest(
        @Schema(description = "Nombre de la estrategia (opcional, usa default si no se especifica)", example = "matchMetrics")
        String strategyName
) {}
