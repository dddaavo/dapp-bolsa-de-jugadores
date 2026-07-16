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
    private final boolean seedEnabled;

    public WhoScoredAdapter(WhoScoredScraper scraper,
                            @Value("${whoscored.scraping.enabled:false}") boolean scrapingEnabled,
                            @Value("${whoscored.seed.enabled:true}") boolean seedEnabled) {
        this.scraper = scraper;
        this.scrapingEnabled = scrapingEnabled;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public List<ScrapedPlayer> fetchPlayersByLeague(League league) {
        if (!scrapingEnabled) {
            log.info("[WhoScoredAdapter] Scraping desactivado — usando seed estático para {}", league);
            return seedOrEmpty(league);
        }

        log.info("[WhoScoredAdapter] Iniciando scraping para {}", league);
        try {
            List<ScrapedPlayer> players = scraper.fetchPlayersByLeague(league);
            if (players.isEmpty()) {
                log.warn("[WhoScoredAdapter] Scraper devolvió 0 jugadores para {} — usando seed estático", league);
                return seedOrEmpty(league);
            }
            return players;
        } catch (Exception e) {
            log.error("[WhoScoredAdapter] Error en scraping de {}: {} — usando seed estático", league, e.getMessage());
            return seedOrEmpty(league);
        }
    }

    // Fallback a datos locales (§7: tolerar fallas del proveedor y continuar con datos locales).
    // Se puede desactivar con whoscored.seed.enabled=false (usado en los profiles de test).
    private List<ScrapedPlayer> seedOrEmpty(League league) {
        return seedEnabled ? StaticPlayerData.forLeague(league) : List.of();
    }
}
