package com.unq.dapp.bolsa.portfolio.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        @Schema(description = "ID del usuario") Long userId,
        @Schema(description = "Posiciones actuales del usuario") List<PositionResponse> positions,
        @Schema(description = "Suma del costo total de todas las posiciones") BigDecimal totalInvested,
        @Schema(description = "Valor total de la cartera al precio actual") BigDecimal totalCurrentValue,
        @Schema(description = "Ganancia o pérdida total de la cartera") BigDecimal totalProfitLoss
) {}
