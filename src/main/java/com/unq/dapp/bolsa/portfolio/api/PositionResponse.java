package com.unq.dapp.bolsa.portfolio.api;

import java.math.BigDecimal;

public record PositionResponse(
        Long playerId,
        String playerName,
        Integer quantity,
        BigDecimal avgBuyPrice,
        BigDecimal currentTokenValue,
        BigDecimal currentValue,
        BigDecimal totalCost,
        BigDecimal profitLoss,
        BigDecimal profitLossPct
) {}
