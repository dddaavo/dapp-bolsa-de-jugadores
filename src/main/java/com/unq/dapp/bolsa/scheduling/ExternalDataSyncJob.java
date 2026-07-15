package com.unq.dapp.bolsa.scheduling;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import com.unq.dapp.bolsa.pricing.domain.PlayerMetricsSnapshot;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class ExternalDataSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ExternalDataSyncJob.class);

    private final PlayerStatsPort playerStatsPort;
    private final PlayerRepository playerRepository;
    private final PlayerMetricsSnapshotRepository metricsRepository;

    public ExternalDataSyncJob(PlayerStatsPort playerStatsPort,
                               PlayerRepository playerRepository,
                               PlayerMetricsSnapshotRepository metricsRepository) {
        this.playerStatsPort = playerStatsPort;
        this.playerRepository = playerRepository;
        this.metricsRepository = metricsRepository;
    }

    @Scheduled(cron = "${external.sync.cron:0 0 2 * * *}")
    @SchedulerLock(name = "ExternalDataSyncJob", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    @Transactional
    public void execute() {
        log.info("[ExternalDataSyncJob] Iniciando sincronización de datos externos");
        long start = System.currentTimeMillis();

        int playersUpdated = 0;
        int metricsUpdated = 0;

        for (League league : League.values()) {
            try {
                var scraped = playerStatsPort.fetchPlayersByLeague(league);
                log.info("[ExternalDataSyncJob] {} jugadores recibidos para {}", scraped.size(), league);

                for (ScrapedPlayer sp : scraped) {
                    Player player = upsertPlayer(sp);
                    upsertMetrics(player, sp);
                    playersUpdated++;
                    metricsUpdated++;
                }
            } catch (Exception e) {
                log.error("[ExternalDataSyncJob] Error sincronizando {}: {}", league, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[ExternalDataSyncJob] Completado: {} jugadores, {} métricas actualizadas en {}ms",
                playersUpdated, metricsUpdated, elapsed);
    }

    private Player upsertPlayer(ScrapedPlayer sp) {
        Player player = playerRepository.findByExternalId(sp.whoScoredId())
                .orElseGet(Player::new);

        player.setExternalId(sp.whoScoredId());
        player.setName(sp.name());
        player.setTeam(sp.team());
        player.setPosition(sp.position());
        player.setLeague(sp.league());
        player.setNationality(sp.nationality());
        player.setActive(true);

        return playerRepository.save(player);
    }

    private void upsertMetrics(Player player, ScrapedPlayer sp) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate periodStart = today.withDayOfMonth(1);

        PlayerMetricsSnapshot snapshot = metricsRepository
                .findTopByPlayerIdOrderByPeriodEndDesc(player.getId())
                .orElseGet(PlayerMetricsSnapshot::new);

        // Si hay métricas reales del scraper (goals > 0 o rating > 0), las usamos.
        // Si el scraper devolvió ceros (columnas no disponibles), mantenemos los valores anteriores.
        boolean hasRealMetrics = sp.goals() > 0 || sp.assists() > 0 || sp.rating() > 0.0;

        snapshot.setPlayerId(player.getId());
        snapshot.setPeriodStart(periodStart);
        snapshot.setPeriodEnd(today);

        if (hasRealMetrics) {
            snapshot.setGoals(sp.goals());
            snapshot.setAssists(sp.assists());
            snapshot.setMatches(sp.matches());
            snapshot.setMinutesPlayed(sp.minutesPlayed());
            snapshot.setRating(BigDecimal.valueOf(sp.rating()));
        }

        metricsRepository.save(snapshot);
    }
}
