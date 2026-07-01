package com.unq.dapp.bolsa.pricing.api;

import java.math.BigDecimal;

public record PlayerRankingResponse(
        int rankPosition,
        Long playerId,
        String playerName,
        BigDecimal value,
        String currency
) {}
