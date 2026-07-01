package com.unq.dapp.bolsa.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateStrategyWeightsRequest(
        @NotNull
        @Schema(description = "Pesos de la estrategia. GlobalMetrics: {\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}. PositionMetrics: {\"FW\":{\"goals\":0.6,\"assists\":0.3,\"rating\":0.1},...}")
        Map<String, Object> weights
) {}
