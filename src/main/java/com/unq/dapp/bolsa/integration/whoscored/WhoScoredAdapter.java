package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter principal que orquesta el scraping de WhoScored.
 * Implementa Fallback Chain: si el scraping falla o esta desactivado,
 * delega en WhoScoredFixturesFallback (datos estaticos).
 *
 * Controlado por: whoscored.scraping.enabled (default: false)
 *
 * @Primary: este bean gana sobre WhoScoredFixturesFallback cuando Spring
 * inyecta PlayerStatsPort en DataInitializer u otros servicios.
 */
@Primary
@Component
public class WhoScoredAdapter implements PlayerStatsPort {

    private static final Logger log = LoggerFactory.getLogger(WhoScoredAdapter.class);

    private final WhoScoredFixturesFallback fallback;
    private final boolean scrapingEnabled;

    public WhoScoredAdapter(WhoScoredFixturesFallback fallback,
                            @Value("${whoscored.scraping.enabled:false}") boolean scrapingEnabled) {
        this.fallback = fallback;
        this.scrapingEnabled = scrapingEnabled;
    }

    @Override
    public List<ScrapedPlayer> fetchPlayersByLeague(League league) {
        if (!scrapingEnabled) {
            log.debug("[WhoScoredAdapter] Scraping desactivado — usando fallback para {}", league);
            return fallback.fetchPlayersByLeague(league);
        }

        log.info("[WhoScoredAdapter] Scraping activado — iniciando para {}", league);
        try {
            WhoScoredPlaywrightScraper scraper = new WhoScoredPlaywrightScraper();
            List<ScrapedPlayer> players = scraper.fetchPlayersByLeague(league);

            if (players.isEmpty()) {
                log.warn("[WhoScoredAdapter] Scraper devolvio 0 jugadores para {} — activando fallback", league);
                return fallback.fetchPlayersByLeague(league);
            }

            return players;
        } catch (Exception e) {
            log.error("[WhoScoredAdapter] Error en scraping de {} — activando fallback: {}", league, e.getMessage());
            return fallback.fetchPlayersByLeague(league);
        }
    }
}


