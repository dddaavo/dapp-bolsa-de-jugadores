package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.pricing.domain.Quote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuoteResponse(
        Long playerId,
        BigDecimal value,
        String currency,
        LocalDateTime calculatedAt,
        String strategyName
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
