package com.unq.dapp.bolsa.scheduling;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import com.unq.dapp.bolsa.pricing.domain.PlayerMetricsSnapshot;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalDataSyncJobTest {

    @Mock private PlayerStatsPort playerStatsPort;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerMetricsSnapshotRepository metricsRepository;

    @InjectMocks
    private ExternalDataSyncJob job;

    @Test
    void deberiaCrearJugadorNuevoCuandoNoExisteEnBD() {
        ScrapedPlayer scraped = new ScrapedPlayer(
                "ws-001", "Erling Haaland", "Man City", Position.FW,
                League.PREMIER_LEAGUE, "", 8, 3, 20, 1780, 8.1);
        when(playerStatsPort.fetchPlayersByLeague(League.PREMIER_LEAGUE))
                .thenReturn(List.of(scraped));
        for (League l : League.values()) {
            if (l != League.PREMIER_LEAGUE) when(playerStatsPort.fetchPlayersByLeague(l)).thenReturn(List.of());
        }

        Player savedPlayer = new Player();
        savedPlayer.setId(1L);
        when(playerRepository.findByExternalId("ws-001")).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenReturn(savedPlayer);
        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(1L)).thenReturn(Optional.empty());
        when(metricsRepository.save(any())).thenReturn(new PlayerMetricsSnapshot());

        job.execute();

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue().getName()).isEqualTo("Erling Haaland");
        assertThat(playerCaptor.getValue().getExternalId()).isEqualTo("ws-001");
    }

    @Test
    void deberiaActualizarJugadorExistenteCuandoYaEstaEnBD() {
        ScrapedPlayer scraped = new ScrapedPlayer(
                "ws-002", "Bukayo Saka", "Arsenal", Position.MF,
                League.PREMIER_LEAGUE, "", 10, 7, 30, 2580, 7.8);
        when(playerStatsPort.fetchPlayersByLeague(League.PREMIER_LEAGUE))
                .thenReturn(List.of(scraped));
        for (League l : League.values()) {
            if (l != League.PREMIER_LEAGUE) when(playerStatsPort.fetchPlayersByLeague(l)).thenReturn(List.of());
        }

        Player existing = new Player();
        existing.setId(2L);
        existing.setName("Saka");
        when(playerRepository.findByExternalId("ws-002")).thenReturn(Optional.of(existing));
        when(playerRepository.save(any())).thenReturn(existing);
        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(2L)).thenReturn(Optional.empty());
        when(metricsRepository.save(any())).thenReturn(new PlayerMetricsSnapshot());

        job.execute();

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Bukayo Saka");
        assertThat(captor.getValue().getTeam()).isEqualTo("Arsenal");
    }

    @Test
    void deberiaGuardarMetricasRealesCuandoElScraperLasProvee() {
        ScrapedPlayer scraped = new ScrapedPlayer(
                "ws-003", "Mohamed Salah", "Liverpool", Position.FW,
                League.PREMIER_LEAGUE, "", 18, 9, 34, 2980, 8.5);
        when(playerStatsPort.fetchPlayersByLeague(League.PREMIER_LEAGUE))
                .thenReturn(List.of(scraped));
        for (League l : League.values()) {
            if (l != League.PREMIER_LEAGUE) when(playerStatsPort.fetchPlayersByLeague(l)).thenReturn(List.of());
        }

        Player player = new Player();
        player.setId(3L);
        when(playerRepository.findByExternalId("ws-003")).thenReturn(Optional.of(player));
        when(playerRepository.save(any())).thenReturn(player);
        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(3L)).thenReturn(Optional.empty());
        when(metricsRepository.save(any())).thenReturn(new PlayerMetricsSnapshot());

        job.execute();

        ArgumentCaptor<PlayerMetricsSnapshot> metricCaptor = ArgumentCaptor.forClass(PlayerMetricsSnapshot.class);
        verify(metricsRepository).save(metricCaptor.capture());
        PlayerMetricsSnapshot saved = metricCaptor.getValue();
        assertThat(saved.getGoals()).isEqualTo(18);
        assertThat(saved.getAssists()).isEqualTo(9);
        assertThat(saved.getMatches()).isEqualTo(34);
        assertThat(saved.getRating()).isEqualByComparingTo("8.5");
    }

    @Test
    void deberiaContinuarConOtrasLigasCuandoUnaFalla() {
        when(playerStatsPort.fetchPlayersByLeague(League.PREMIER_LEAGUE))
                .thenThrow(new RuntimeException("Cloudflare bloqueó el scraping"));
        for (League l : League.values()) {
            if (l != League.PREMIER_LEAGUE) when(playerStatsPort.fetchPlayersByLeague(l)).thenReturn(List.of());
        }

        job.execute();

        // El job no lanza excepción y sigue con las demás ligas
        verify(playerStatsPort, times(League.values().length)).fetchPlayersByLeague(any());
        verify(playerRepository, never()).save(any());
    }

    @Test
    void deberiaNoActualizarMetricasCuandoElScraperDevuelveCeros() {
        ScrapedPlayer scraped = new ScrapedPlayer(
                "ws-004", "Player X", "Team Y", Position.DF,
                League.BUNDESLIGA, "");  // constructor sin métricas → todos 0
        when(playerStatsPort.fetchPlayersByLeague(League.BUNDESLIGA))
                .thenReturn(List.of(scraped));
        for (League l : League.values()) {
            if (l != League.BUNDESLIGA) when(playerStatsPort.fetchPlayersByLeague(l)).thenReturn(List.of());
        }

        Player player = new Player();
        player.setId(4L);
        when(playerRepository.findByExternalId("ws-004")).thenReturn(Optional.of(player));
        when(playerRepository.save(any())).thenReturn(player);

        PlayerMetricsSnapshot existingMetrics = new PlayerMetricsSnapshot();
        existingMetrics.setGoals(5);
        when(metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(4L))
                .thenReturn(Optional.of(existingMetrics));
        when(metricsRepository.save(any())).thenReturn(existingMetrics);

        job.execute();

        ArgumentCaptor<PlayerMetricsSnapshot> captor = ArgumentCaptor.forClass(PlayerMetricsSnapshot.class);
        verify(metricsRepository).save(captor.capture());
        // Con métricas todas en 0, no se sobreescriben los valores previos
        assertThat(captor.getValue().getGoals()).isEqualTo(5);
    }
}
