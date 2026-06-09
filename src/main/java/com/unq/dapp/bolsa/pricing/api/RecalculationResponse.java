package com.unq.dapp.bolsa.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record RecalculationResponse(
        @Schema(description = "Cantidad de jugadores recalculados", example = "50") int totalRecalculated,
        @Schema(description = "Estrategia usada", example = "matchMetrics") String strategyUsed,
        @Schema(description = "Timestamp de la recalculación") LocalDateTime timestamp
) {}
