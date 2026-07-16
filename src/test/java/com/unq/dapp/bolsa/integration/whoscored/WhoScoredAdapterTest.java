package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhoScoredAdapterTest {

    @Test
    void deberiaRetornarListaVaciaCuandoScrapingYSeedDesactivados() {
        WhoScoredAdapter adapter = new WhoScoredAdapter(null, false, false);

        assertThat(adapter.fetchPlayersByLeague(League.PREMIER_LEAGUE)).isEmpty();
    }

    @Test
    void deberiaRetornarSeedCuandoScrapingDesactivadoYSeedActivado() {
        WhoScoredAdapter adapter = new WhoScoredAdapter(null, false, true);

        for (League league : League.values()) {
            assertThat(adapter.fetchPlayersByLeague(league))
                    .as("seed para %s", league)
                    .isNotEmpty();
        }
    }

    @Test
    void deberiaRetornarJugadoresCuandoScrapingActivadoYExitoso() {
        WhoScoredScraper scraper = mock(WhoScoredScraper.class);
        ScrapedPlayer player = new ScrapedPlayer("1", "Erling Haaland", "Manchester City", Position.FW, League.PREMIER_LEAGUE, "");
        when(scraper.fetchPlayersByLeague(League.PREMIER_LEAGUE)).thenReturn(List.of(player));
        WhoScoredAdapter adapter = new WhoScoredAdapter(scraper, true, false);

        List<ScrapedPlayer> result = adapter.fetchPlayersByLeague(League.PREMIER_LEAGUE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Erling Haaland");
    }

    @Test
    void deberiaCaerAlSeedCuandoScrapingFallaYSeedActivado() {
        WhoScoredScraper scraper = mock(WhoScoredScraper.class);
        when(scraper.fetchPlayersByLeague(any())).thenThrow(new RuntimeException("Chrome no disponible"));
        WhoScoredAdapter adapter = new WhoScoredAdapter(scraper, true, true);

        assertThat(adapter.fetchPlayersByLeague(League.BUNDESLIGA)).isNotEmpty();
    }

    @Test
    void deberiaRetornarVacioCuandoScrapingFallaYSeedDesactivado() {
        WhoScoredScraper scraper = mock(WhoScoredScraper.class);
        when(scraper.fetchPlayersByLeague(any())).thenThrow(new RuntimeException("Chrome no disponible"));
        WhoScoredAdapter adapter = new WhoScoredAdapter(scraper, true, false);

        assertThat(adapter.fetchPlayersByLeague(League.BUNDESLIGA)).isEmpty();
    }

    @Test
    void deberiaCaerAlSeedCuandoScraperDevuelveVacioYSeedActivado() {
        WhoScoredScraper scraper = mock(WhoScoredScraper.class);
        when(scraper.fetchPlayersByLeague(any())).thenReturn(List.of());
        WhoScoredAdapter adapter = new WhoScoredAdapter(scraper, true, true);

        assertThat(adapter.fetchPlayersByLeague(League.LA_LIGA)).isNotEmpty();
    }
}
