package com.unq.dapp.bolsa.metrics.application;

import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.metrics.api.MarketMetricsResponse;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import com.unq.dapp.bolsa.trading.infrastructure.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketMetricsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private QuoteRepository quoteRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerTokenInventoryRepository inventoryRepository;

    @InjectMocks
    private MarketMetricsService service;

    @Test
    void deberiaRetornarConteoDeOrdenesPorTipo() {
        when(orderRepository.countByType(OrderType.BUY)).thenReturn(80L);
        when(orderRepository.countByType(OrderType.SELL)).thenReturn(40L);
        when(orderRepository.sumTotalAmount()).thenReturn(BigDecimal.valueOf(487.50));
        when(inventoryRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findTopTradedPlayerIds(any(Pageable.class))).thenReturn(List.of());
        when(quoteRepository.findDistinctPlayerIds()).thenReturn(List.of());

        MarketMetricsResponse result = service.getMarketMetrics();

        assertThat(result.totalOrders()).isEqualTo(120L);
        assertThat(result.ordersByType()).containsEntry("compras", 80L);
        assertThat(result.ordersByType()).containsEntry("ventas", 40L);
    }

    @Test
    void deberiaCalcularVolumenTotalDeOrdenes() {
        when(orderRepository.countByType(any())).thenReturn(0L);
        when(orderRepository.sumTotalAmount()).thenReturn(BigDecimal.valueOf(1234.56));
        when(inventoryRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findTopTradedPlayerIds(any(Pageable.class))).thenReturn(List.of());
        when(quoteRepository.findDistinctPlayerIds()).thenReturn(List.of());

        MarketMetricsResponse result = service.getMarketMetrics();

        assertThat(result.totalVolumeCredits()).isEqualByComparingTo("1234.56");
    }

    @Test
    void deberiaCalcularCapitalizacionDeMercado() {
        when(orderRepository.countByType(any())).thenReturn(0L);
        when(orderRepository.sumTotalAmount()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.findTopTradedPlayerIds(any(Pageable.class))).thenReturn(List.of());
        when(quoteRepository.findDistinctPlayerIds()).thenReturn(List.of());

        PlayerTokenInventory inv = new PlayerTokenInventory();
        inv.setPlayerId(1L);
        inv.setTotalEmitted(100);
        inv.setInitialTokenValue(BigDecimal.ONE);

        Quote latestQuote = buildQuote(1L, BigDecimal.valueOf(1.75), LocalDateTime.now());
        when(inventoryRepository.findAll()).thenReturn(List.of(inv));
        when(quoteRepository.findTopByPlayerIdOrderByCalculatedAtDesc(1L))
                .thenReturn(Optional.of(latestQuote));

        MarketMetricsResponse result = service.getMarketMetrics();

        // 1.75 × 100 tokens = 175.00
        assertThat(result.marketCapCredits()).isEqualByComparingTo("175.00");
    }

    @Test
    void deberiaIdentificarTopJugadoresMasOperados() {
        when(orderRepository.countByType(any())).thenReturn(0L);
        when(orderRepository.sumTotalAmount()).thenReturn(BigDecimal.ZERO);
        when(inventoryRepository.findAll()).thenReturn(List.of());
        when(quoteRepository.findDistinctPlayerIds()).thenReturn(List.of());

        when(orderRepository.findTopTradedPlayerIds(any(Pageable.class)))
                .thenReturn(List.of(new Object[]{1L, 12L}, new Object[]{2L, 8L}));

        Player player1 = buildPlayer(1L, "Haaland");
        Player player2 = buildPlayer(2L, "Saka");
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));

        MarketMetricsResponse result = service.getMarketMetrics();

        assertThat(result.topTradedPlayers()).hasSize(2);
        assertThat(result.topTradedPlayers().get(0).playerName()).isEqualTo("Haaland");
        assertThat(result.topTradedPlayers().get(0).totalOrders()).isEqualTo(12L);
        // playerId ya no se expone en la respuesta
    }

    @Test
    void deberiaIdentificarTopMoversConMayorVariacion() {
        when(orderRepository.countByType(any())).thenReturn(0L);
        when(orderRepository.sumTotalAmount()).thenReturn(BigDecimal.ZERO);
        when(inventoryRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findTopTradedPlayerIds(any(Pageable.class))).thenReturn(List.of());

        when(quoteRepository.findDistinctPlayerIds()).thenReturn(List.of(1L, 2L));

        LocalDateTime t1 = LocalDateTime.now().minusDays(1);
        LocalDateTime t2 = LocalDateTime.now();

        // Jugador 1: sube de 1.50 a 1.80 → +20%
        when(quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(1L)).thenReturn(List.of(
                buildQuote(1L, BigDecimal.valueOf(1.80), t2),
                buildQuote(1L, BigDecimal.valueOf(1.50), t1)
        ));
        // Jugador 2: sube de 1.60 a 1.68 → +5%
        when(quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(2L)).thenReturn(List.of(
                buildQuote(2L, BigDecimal.valueOf(1.68), t2),
                buildQuote(2L, BigDecimal.valueOf(1.60), t1)
        ));

        when(playerRepository.findById(1L)).thenReturn(Optional.of(buildPlayer(1L, "Salah")));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(buildPlayer(2L, "Kane")));

        MarketMetricsResponse result = service.getMarketMetrics();

        assertThat(result.topMovers()).hasSize(2);
        assertThat(result.topMovers().get(0).playerName()).isEqualTo("Salah");
        assertThat(result.topMovers().get(0).variacionPct()).isEqualTo("+20.00");
        assertThat(result.topMovers().get(0).tendencia()).isEqualTo("SUBE");
    }

    @Test
    void deberiaOmitirJugadoresSinHistorialSuficienteEnTopMovers() {
        when(orderRepository.countByType(any())).thenReturn(0L);
        when(orderRepository.sumTotalAmount()).thenReturn(BigDecimal.ZERO);
        when(inventoryRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findTopTradedPlayerIds(any(Pageable.class))).thenReturn(List.of());
        when(quoteRepository.findDistinctPlayerIds()).thenReturn(List.of(1L));

        // Solo una cotización — no hay variación que calcular
        when(quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(1L)).thenReturn(List.of(
                buildQuote(1L, BigDecimal.valueOf(1.50), LocalDateTime.now())
        ));

        MarketMetricsResponse result = service.getMarketMetrics();

        assertThat(result.topMovers()).isEmpty();
    }

    // — helpers —

    private Quote buildQuote(Long playerId, BigDecimal value, LocalDateTime calculatedAt) {
        Quote q = new Quote();
        q.setPlayerId(playerId);
        q.setValue(new Money(value, "CREDITS"));
        q.setCalculatedAt(calculatedAt);
        q.setStrategyName("GlobalMetrics");
        q.setStrategyVersion("v1.0");
        return q;
    }

    private Player buildPlayer(Long id, String name) {
        Player p = new Player();
        p.setId(id);
        p.setName(name);
        return p;
    }
}
