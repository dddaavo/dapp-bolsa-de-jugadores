package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WhoScoredAdapter implements PlayerStatsPort {

    private static final Logger log = LoggerFactory.getLogger(WhoScoredAdapter.class);

    private final WhoScoredScraper scraper;
    private final boolean scrapingEnabled;

    public WhoScoredAdapter(WhoScoredScraper scraper,
                            @Value("${whoscored.scraping.enabled:false}") boolean scrapingEnabled) {
        this.scraper = scraper;
        this.scrapingEnabled = scrapingEnabled;
    }

    @Override
    public List<ScrapedPlayer> fetchPlayersByLeague(League league) {
        if (!scrapingEnabled) {
            log.debug("[WhoScoredAdapter] Scraping desactivado — omitiendo {}", league);
            return List.of();
        }

        log.info("[WhoScoredAdapter] Iniciando scraping para {}", league);
        try {
            List<ScrapedPlayer> players = scraper.fetchPlayersByLeague(league);
            if (players.isEmpty()) {
                log.warn("[WhoScoredAdapter] Scraper devolvió 0 jugadores para {}", league);
            }
            return players;
        } catch (Exception e) {
            log.error("[WhoScoredAdapter] Error en scraping de {}: {}", league, e.getMessage());
            return List.of();
        }
    }
}
