package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.*;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteRecalculationOrchestratorTest {

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerMetricsSnapshotRepository metricsRepository;
    @Mock
    private PlayerTokenInventoryRepository inventoryRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private StrategyRegistry strategyRegistry;

    private QuoteRecalculationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new QuoteRecalculationOrchestrator(
                playerRepository, metricsRepository, inventoryRepository,
                quoteRepository, strategyRegistry
        );
    }

    @Test
    void deberiaRecalcularTodosLosJugadores() {
        // Given
        Player player1 = crearPlayer(1L);
        Player player2 = crearPlayer(2L);
        List<Player> players = Arrays.asList(player1, player2);

        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        when(strategyRegistry.getDefault()).thenReturn(strategy);
        when(playerRepository.findAll()).thenReturn(players);

        for (Player player : players) {
            PlayerMetricsSnapshot metrics = crearMetrics(player.getId());
            PlayerTokenInventory inventory = crearInventory(player.getId());

            when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(player.getId()))
                    .thenReturn(Optional.of(metrics));
            when(inventoryRepository.findById(player.getId()))
                    .thenReturn(Optional.of(inventory));
        }

        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        int recalculated = orchestrator.recalculateAll(null);

        // Then
        assertThat(recalculated).isEqualTo(2);
        verify(quoteRepository, times(2)).save(any(Quote.class));
    }

    @Test
    void deberiaRecalcularUnJugadorEspecifico() {
        // Given
        Long playerId = 1L;
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        when(strategyRegistry.getDefault()).thenReturn(strategy);

        PlayerMetricsSnapshot metrics = crearMetrics(playerId);
        PlayerTokenInventory inventory = crearInventory(playerId);

        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(playerId))
                .thenReturn(Optional.of(metrics));
        when(inventoryRepository.findById(playerId))
                .thenReturn(Optional.of(inventory));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Quote resultado = orchestrator.recalculateForPlayer(playerId, null);

        // Then
        assertThat(resultado).isNotNull();
        verify(quoteRepository).save(any(Quote.class));
    }

    @Test
    void deberiaUsarEstrategiaEspecificada() {
        // Given
        Long playerId = 1L;
        String strategyName = "CustomStrategy";
        PricingStrategy customStrategy = new MatchMetricsStrategy(); // Simulamos una custom

        when(strategyRegistry.get(strategyName)).thenReturn(Optional.of(customStrategy));

        PlayerMetricsSnapshot metrics = crearMetrics(playerId);
        PlayerTokenInventory inventory = crearInventory(playerId);

        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(playerId))
                .thenReturn(Optional.of(metrics));
        when(inventoryRepository.findById(playerId))
                .thenReturn(Optional.of(inventory));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Quote resultado = orchestrator.recalculateForPlayer(playerId, strategyName);

        // Then
        assertThat(resultado).isNotNull();
        verify(strategyRegistry).get(strategyName);
    }

    @Test
    void deberiaLanzarExcepcionSiNoExistenMetricas() {
        // Given
        Long playerId = 1L;
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        when(strategyRegistry.getDefault()).thenReturn(strategy);
        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(playerId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> orchestrator.recalculateForPlayer(playerId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No metrics found");
    }

    @Test
    void deberiaLanzarExcepcionSiNoExisteInventario() {
        // Given
        Long playerId = 1L;
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        when(strategyRegistry.getDefault()).thenReturn(strategy);

        PlayerMetricsSnapshot metrics = crearMetrics(playerId);
        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(playerId))
                .thenReturn(Optional.of(metrics));
        when(inventoryRepository.findById(playerId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> orchestrator.recalculateForPlayer(playerId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No inventory found");
    }

    @Test
    void deberiaLanzarExcepcionSiEstrategiaNoExiste() {
        // Given
        String invalidStrategy = "NonExistent";
        when(strategyRegistry.get(invalidStrategy)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> orchestrator.recalculateForPlayer(1L, invalidStrategy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strategy not found");
    }

    private Player crearPlayer(Long id) {
        Player player = new Player();
        player.setId(id);
        player.setName("Player " + id);
        return player;
    }

    private PlayerMetricsSnapshot crearMetrics(Long playerId) {
        PlayerMetricsSnapshot metrics = new PlayerMetricsSnapshot();
        metrics.setPlayerId(playerId);
        metrics.setGoals(10);
        metrics.setAssists(5);
        metrics.setRating(BigDecimal.valueOf(7.5));
        metrics.setMatches(10);
        metrics.setPeriodStart(LocalDate.of(2025, Month.DECEMBER, 15));
        metrics.setPeriodEnd(LocalDate.of(2026, Month.JANUARY, 15));
        return metrics;
    }

    private PlayerTokenInventory crearInventory(Long playerId) {
        PlayerTokenInventory inventory = new PlayerTokenInventory();
        inventory.setPlayerId(playerId);
        inventory.setTotalEmitted(100);
        inventory.setHeldBySystem(100);
        inventory.setInitialTokenValue(BigDecimal.ONE);
        return inventory;
    }
}

