package com.unq.dapp.bolsa.portfolio.api;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        Long userId,
        List<PositionResponse> positions,
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalProfitLoss
) {}
