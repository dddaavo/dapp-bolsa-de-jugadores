package com.unq.dapp.bolsa.metrics.api;

import java.math.BigDecimal;

public record TopMoverItem(
        long playerId,
        String playerName,
        BigDecimal currentValue,
        BigDecimal previousValue,
        BigDecimal variationPct
) {}
