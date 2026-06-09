package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.pricing.domain.Quote;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuoteResponse(
        @Schema(description = "ID del jugador", example = "1") Long playerId,
        @Schema(description = "Valor de la cotización", example = "12.50") BigDecimal value,
        @Schema(description = "Moneda", example = "CREDITS") String currency,
        @Schema(description = "Fecha y hora del cálculo") LocalDateTime calculatedAt,
        @Schema(description = "Estrategia usada para el cálculo", example = "matchMetrics") String strategyName
) {
    public static QuoteResponse from(Quote q) {
        return new QuoteResponse(
                q.getPlayerId(),
                q.getValue().amount(),
                q.getValue().currency(),
                q.getCalculatedAt(),
                q.getStrategyName()
        );
    }
}
