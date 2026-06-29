package com.unq.dapp.bolsa.metrics.application;

import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.metrics.api.MarketMetricsResponse;
import com.unq.dapp.bolsa.metrics.api.TopMoverItem;
import com.unq.dapp.bolsa.metrics.api.TopTradedPlayerItem;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import com.unq.dapp.bolsa.trading.infrastructure.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MarketMetricsService {

    private static final int TOP_N = 5;

    private final OrderRepository orderRepository;
    private final QuoteRepository quoteRepository;
    private final PlayerRepository playerRepository;
    private final PlayerTokenInventoryRepository inventoryRepository;

    public MarketMetricsService(OrderRepository orderRepository,
                                QuoteRepository quoteRepository,
                                PlayerRepository playerRepository,
                                PlayerTokenInventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.quoteRepository = quoteRepository;
        this.playerRepository = playerRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public MarketMetricsResponse getMarketMetrics() {
        long totalBuy  = orderRepository.countByType(OrderType.BUY);
        long totalSell = orderRepository.countByType(OrderType.SELL);

        BigDecimal totalVolume = orderRepository.sumTotalAmount();
        BigDecimal marketCap   = computeMarketCap();

        return new MarketMetricsResponse(
                totalBuy + totalSell,
                Map.of("BUY", totalBuy, "SELL", totalSell),
                totalVolume.setScale(2, RoundingMode.HALF_UP),
                marketCap.setScale(2, RoundingMode.HALF_UP),
                computeTopTraded(),
                computeTopMovers()
        );
    }

    private BigDecimal computeMarketCap() {
        return inventoryRepository.findAll().stream()
                .map(inv -> {
                    BigDecimal price = quoteRepository
                            .findTopByPlayerIdOrderByCalculatedAtDesc(inv.getPlayerId())
                            .map(q -> q.getValue().amount())
                            .orElse(inv.getInitialTokenValue());
                    return price.multiply(BigDecimal.valueOf(inv.getTotalEmitted()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TopTradedPlayerItem> computeTopTraded() {
        List<Object[]> rows = orderRepository.findTopTradedPlayerIds(PageRequest.of(0, TOP_N));
        return rows.stream()
                .map(row -> {
                    Long playerId = (Long) row[0];
                    Long count    = (Long) row[1];
                    String name = playerRepository.findById(playerId)
                            .map(Player::getName).orElse("Unknown");
                    return new TopTradedPlayerItem(playerId, name, count);
                })
                .toList();
    }

    private List<TopMoverItem> computeTopMovers() {
        return quoteRepository.findDistinctPlayerIds().stream()
                .map(this::buildMoverItem)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        item -> item.variationPct().abs(),
                        Comparator.reverseOrder()))
                .limit(TOP_N)
                .toList();
    }

    private TopMoverItem buildMoverItem(Long playerId) {
        List<Quote> quotes = quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(playerId);
        if (quotes.size() < 2) return null;

        BigDecimal current  = quotes.get(0).getValue().amount();
        BigDecimal previous = quotes.get(1).getValue().amount();
        if (previous.compareTo(BigDecimal.ZERO) == 0) return null;

        BigDecimal variationPct = current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        String name = playerRepository.findById(playerId)
                .map(Player::getName).orElse("Unknown");

        return new TopMoverItem(playerId, name, current, previous, variationPct);
    }
}
