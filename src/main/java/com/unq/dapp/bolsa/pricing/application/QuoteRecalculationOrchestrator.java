package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.*;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
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
    private final StrategyConfigService strategyConfigService;
    private final QuoteService quoteService;
    private final MeterRegistry meterRegistry;
    private final Timer recalculationTimer;

    private static final int DEFAULT_WARMUP_LIMIT = 10;

    public QuoteRecalculationOrchestrator(
            PlayerRepository playerRepository,
            PlayerMetricsSnapshotRepository metricsRepository,
            PlayerTokenInventoryRepository inventoryRepository,
            QuoteRepository quoteRepository,
            StrategyRegistry strategyRegistry,
            StrategyConfigService strategyConfigService,
            QuoteService quoteService,
            MeterRegistry meterRegistry) {
        this.playerRepository = playerRepository;
        this.metricsRepository = metricsRepository;
        this.inventoryRepository = inventoryRepository;
        this.quoteRepository = quoteRepository;
        this.strategyRegistry = strategyRegistry;
        this.strategyConfigService = strategyConfigService;
        this.quoteService = quoteService;
        this.meterRegistry = meterRegistry;
        this.recalculationTimer = Timer.builder("quotes.recalculation.duration")
                .description("Duración de recalculación masiva de cotizaciones")
                .register(meterRegistry);
    }

    /**
     * Recalcula las cotizaciones de todos los jugadores.
     *
     * @param strategyName  Nombre de la estrategia a usar (null = default)
     * @return Cantidad de jugadores recalculados
     */
    @CacheEvict(cacheNames = "ranking", allEntries = true, beforeInvocation = true)
    @Transactional
    public int recalculateAll(String strategyName) {
        int recalculated = recalculationTimer.record(() -> {
            PricingStrategy strategy = resolveStrategy(strategyName);
            String resolvedName = strategy.name();
            List<Player> players = playerRepository.findAll();

            if (log.isInfoEnabled()) {
                log.info("[QuoteRecalculation] Iniciando recalculación con estrategia {} v{} para {} jugadores",
                        resolvedName, strategy.version(), players.size());
            }

            int count = 0;
            int errors = 0;
            for (Player player : players) {
                try {
                    recalculateForPlayer(player.getId(), strategy);
                    count++;
                } catch (Exception e) {
                    errors++;
                    log.error("[QuoteRecalculation] Error recalculando jugador {}: {}",
                             player.getId(), e.getMessage());
                }
            }

            String status = errors == 0 ? "complete" : (count == 0 ? "failed" : "partial");
            Counter.builder("quotes.recalculation.total")
                    .description("Cantidad de veces que se ejecutó recalculateAll")
                    .tag("strategy", resolvedName)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();

            log.info("[QuoteRecalculation] Completado: {} jugadores recalculados, {} errores, status={}",
                    count, errors, status);
            return count;
        });

        // Pre-calentar el cache con los datos recién calculados para que el primer GET sea rápido
        try {
            quoteService.getRankingQuotes(DEFAULT_WARMUP_LIMIT, strategyName);
            log.debug("[QuoteRecalculation] Cache de ranking pre-calentado con estrategia {}",
                    strategyName != null ? strategyName : "default");
        } catch (Exception e) {
            log.warn("[QuoteRecalculation] No se pudo pre-calentar el cache de ranking: {}", e.getMessage());
        }

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
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException("Player not found: " + playerId));

        PlayerMetricsSnapshot metrics = metricsRepository
                .findTopByPlayerIdOrderByPeriodEndDesc(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "No metrics found for player " + playerId));

        PlayerTokenInventory inventory = inventoryRepository
                .findById(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "No inventory found for player " + playerId));

        PricingContext context = new PricingContext(
                Money.of(inventory.getInitialTokenValue()),
                player.getPosition()
        );
        Money value = strategy.calculate(metrics, context);

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
            return strategyConfigService.buildDefaultStrategy();
        }
        if (!strategyRegistry.exists(strategyName)) {
            throw new IllegalArgumentException("Strategy not found: " + strategyName);
        }
        return strategyConfigService.buildStrategy(strategyName);
    }
}
