package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhoScoredAdapterTest {

    @Test
    void deberiaRetornarListaVaciaCuandoScrapingDesactivado() {
        WhoScoredAdapter adapter = new WhoScoredAdapter(false);

        List<ScrapedPlayer> result = adapter.fetchPlayersByLeague(League.PREMIER_LEAGUE);

        assertThat(result).isEmpty();
    }

    @Test
    void deberiaRetornarListaVaciaParaTodasLasLigasCuandoScrapingDesactivado() {
        WhoScoredAdapter adapter = new WhoScoredAdapter(false);

        for (League league : League.values()) {
            assertThat(adapter.fetchPlayersByLeague(league)).isEmpty();
        }
    }
}
