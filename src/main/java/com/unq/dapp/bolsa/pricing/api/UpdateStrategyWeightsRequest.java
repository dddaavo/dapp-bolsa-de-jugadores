package com.unq.dapp.bolsa.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateStrategyWeightsRequest(
        @NotBlank
        @Schema(description = "JSON con los nuevos pesos. MatchMetrics: {\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}. PositionWeighted: {\"FW\":{\"goals\":0.6,\"assists\":0.3,\"rating\":0.1},...}")
        String weightsJson
) {}
