package com.unq.dapp.bolsa.metrics.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MarketMetricsResponse(
        long totalOrders,
        Map<String, Long> ordersByType,
        BigDecimal totalVolumeCredits,
        BigDecimal marketCapCredits,
        List<TopTradedPlayerItem> topTradedPlayers,
        List<TopMoverItem> topMovers
) {}
