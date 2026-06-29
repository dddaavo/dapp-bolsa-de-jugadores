package com.unq.dapp.bolsa.portfolio.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PositionResponse(
        @Schema(description = "ID del jugador") Long playerId,
        @Schema(description = "Nombre del jugador") String playerName,
        @Schema(description = "Cantidad de tokens en cartera") Integer quantity,
        @Schema(description = "Precio promedio de compra") BigDecimal avgBuyPrice,
        @Schema(description = "Valor actual por token (cotización vigente)") BigDecimal currentTokenValue,
        @Schema(description = "Valor total de la posición al precio actual") BigDecimal currentValue,
        @Schema(description = "Costo total de compra") BigDecimal totalCost,
        @Schema(description = "Ganancia o pérdida absoluta") BigDecimal profitLoss,
        @Schema(description = "Ganancia o pérdida en porcentaje") BigDecimal profitLossPct
) {}
