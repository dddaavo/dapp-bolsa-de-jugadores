package com.unq.dapp.bolsa.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PlayerRankingResponse(
        @Schema(description = "Posición en el ranking", example = "1") int rankPosition,
        @Schema(description = "ID del jugador", example = "42") Long playerId,
        @Schema(description = "Nombre del jugador", example = "Erling Haaland") String playerName,
        @Schema(description = "Cotización actual", example = "15.75") BigDecimal value,
        @Schema(description = "Moneda", example = "CREDITS") String currency
) {}
