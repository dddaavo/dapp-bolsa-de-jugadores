package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.*;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Orquestador de recalculación de cotizaciones.
 * Coordina la obtención de métricas, aplicación de estrategia y persistencia de quotes.
 */
@Service
public class QuoteRecalculationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(QuoteRecalculationOrchestrator.class);

    private final PlayerRepository playerRepository;
    private final PlayerMetricsSnapshotRepository metricsRepository;
    private final PlayerTokenInventoryRepository inventoryRepository;
    private final QuoteRepository quoteRepository;
    private final StrategyRegistry strategyRegistry;

    public QuoteRecalculationOrchestrator(
            PlayerRepository playerRepository,
            PlayerMetricsSnapshotRepository metricsRepository,
            PlayerTokenInventoryRepository inventoryRepository,
            QuoteRepository quoteRepository,
            StrategyRegistry strategyRegistry) {
        this.playerRepository = playerRepository;
        this.metricsRepository = metricsRepository;
        this.inventoryRepository = inventoryRepository;
        this.quoteRepository = quoteRepository;
        this.strategyRegistry = strategyRegistry;
    }

    /**
     * Recalcula las cotizaciones de todos los jugadores.
     *
     * @param strategyName  Nombre de la estrategia a usar (null = default)
     * @return Cantidad de jugadores recalculados
     */
    @Transactional
    public int recalculateAll(String strategyName) {
        PricingStrategy strategy = resolveStrategy(strategyName);
        List<Player> players = playerRepository.findAll();

        if (log.isInfoEnabled()) {
            log.info("[QuoteRecalculation] Iniciando recalculación con estrategia {} v{} para {} jugadores",
                    strategy.name(), strategy.version(), players.size());
        }

        int recalculated = 0;
        for (Player player : players) {
            try {
                recalculateForPlayer(player.getId(), strategy);
                recalculated++;
            } catch (Exception e) {
                log.error("[QuoteRecalculation] Error recalculando jugador {}: {}",
                         player.getId(), e.getMessage());
            }
        }

        log.info("[QuoteRecalculation] Completado: {} jugadores recalculados", recalculated);
        return recalculated;
    }

    /**
     * Recalcula la cotización de un jugador específico.
     *
     * @param playerId ID del jugador
     * @param strategyName Nombre de la estrategia (null = default)
     * @return Quote creado
     */
    @Transactional
    public Quote recalculateForPlayer(Long playerId, String strategyName) {
        PricingStrategy strategy = resolveStrategy(strategyName);
        return recalculateForPlayer(playerId, strategy);
    }

    private Quote recalculateForPlayer(Long playerId, PricingStrategy strategy) {
        // Obtener última métrica del jugador
        PlayerMetricsSnapshot metrics = metricsRepository
                .findTopByPlayerIdOrderByPeriodEndDesc(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "No metrics found for player " + playerId));

        // Obtener inventario para valor inicial
        PlayerTokenInventory inventory = inventoryRepository
                .findById(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "No inventory found for player " + playerId));

        // Calcular cotización
        PricingContext context = new PricingContext(
                Money.of(inventory.getInitialTokenValue())
        );
        Money value = strategy.calculate(metrics, context);

        // Crear y persistir quote
        Quote quote = new Quote();
        quote.setPlayerId(playerId);
        quote.setValue(value);
        quote.setCalculatedAt(LocalDateTime.now(ZoneOffset.UTC));
        quote.setStrategyName(strategy.name());
        quote.setStrategyVersion(strategy.version());

        return quoteRepository.save(quote);
    }

    private PricingStrategy resolveStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return strategyRegistry.getDefault();
        }
        return strategyRegistry.get(strategyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Strategy not found: " + strategyName));
    }
}

