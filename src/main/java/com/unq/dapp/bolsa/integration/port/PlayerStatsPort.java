package com.unq.dapp.bolsa.integration.port;

import com.unq.dapp.bolsa.catalog.domain.League;

import java.util.List;

/**
 * Port (interfaz del dominio) para obtener jugadores de fuentes externas.
 * Los adapters concretos implementan esta interfaz:
 *  - WhoScoredPlaywrightScraper  → scraping real con browser headless
 *  - WhoScoredFixturesFallback   → datos estáticos cuando el scraping falla
 *  - WhoScoredAdapter            → orquestador: intenta scraping, cae en fallback
 */
public interface PlayerStatsPort {

    /**
     * Devuelve los jugadores de una liga obtenidos de la fuente externa.
     * Nunca lanza excepción — en caso de fallo devuelve lista vacía o datos de fallback.
     */
    List<ScrapedPlayer> fetchPlayersByLeague(League league);
}

