package com.unq.dapp.bolsa.integration.port;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;

/**
 * DTO que representa un jugador obtenido de una fuente externa (scraping / API).
 * Es un record inmutable: el dominio lo convierte a {@link com.unq.dapp.bolsa.catalog.domain.Player}
 * en el DataInitializer o en el job de sincronización.
 */
public record ScrapedPlayer(
        String whoScoredId,
        String name,
        String team,
        Position position,
        League league,
        String nationality,
        int goals,
        int assists,
        int matches,
        int minutesPlayed,
        double rating
) {
    /** Constructor sin métricas — usa ceros como valores por defecto. */
    public ScrapedPlayer(String whoScoredId, String name, String team,
                         Position position, League league, String nationality) {
        this(whoScoredId, name, team, position, league, nationality, 0, 0, 0, 0, 0.0);
    }
}
