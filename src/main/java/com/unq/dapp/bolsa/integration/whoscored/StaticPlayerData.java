package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;

import java.util.List;

/**
 * Dataset estático de jugadores reales usado como <b>fallback</b> del catálogo
 * cuando el scraping de WhoScored está desactivado o falla.
 *
 * <p>Cumple dos objetivos:
 * <ul>
 *   <li>Arranque rápido en local sin esperar el scraping (Playwright/Chrome).</li>
 *   <li>Poblar la base en el contenedor de producción (docker-compose), donde no hay Chrome.</li>
 * </ul>
 *
 * <p>Las métricas de rendimiento no se cargan acá (se generan sintéticamente en el
 * {@code DataInitializer} o se refrescan con el {@code ExternalDataSyncJob}); este seed
 * aporta el <b>catálogo</b> (nombre, equipo, posición, liga).
 */
public final class StaticPlayerData {

    private StaticPlayerData() {
    }

    private static final List<ScrapedPlayer> PLAYERS = List.of(
            // ---------- Premier League ----------
            p("pl-1", "Erling Haaland", "Manchester City", Position.FW, League.PREMIER_LEAGUE),
            p("pl-2", "Mohamed Salah", "Liverpool", Position.FW, League.PREMIER_LEAGUE),
            p("pl-3", "Cole Palmer", "Chelsea", Position.FW, League.PREMIER_LEAGUE),
            p("pl-4", "Bukayo Saka", "Arsenal", Position.FW, League.PREMIER_LEAGUE),
            p("pl-5", "Kevin De Bruyne", "Manchester City", Position.MF, League.PREMIER_LEAGUE),
            p("pl-6", "Bruno Fernandes", "Manchester United", Position.MF, League.PREMIER_LEAGUE),
            p("pl-7", "Declan Rice", "Arsenal", Position.MF, League.PREMIER_LEAGUE),
            p("pl-8", "Virgil van Dijk", "Liverpool", Position.DF, League.PREMIER_LEAGUE),
            p("pl-9", "William Saliba", "Arsenal", Position.DF, League.PREMIER_LEAGUE),
            p("pl-10", "Alisson Becker", "Liverpool", Position.GK, League.PREMIER_LEAGUE),

            // ---------- Bundesliga ----------
            p("bl-1", "Harry Kane", "Bayern Munich", Position.FW, League.BUNDESLIGA),
            p("bl-2", "Serhou Guirassy", "Borussia Dortmund", Position.FW, League.BUNDESLIGA),
            p("bl-3", "Florian Wirtz", "Bayer Leverkusen", Position.MF, League.BUNDESLIGA),
            p("bl-4", "Jamal Musiala", "Bayern Munich", Position.MF, League.BUNDESLIGA),
            p("bl-5", "Xavi Simons", "RB Leipzig", Position.MF, League.BUNDESLIGA),
            p("bl-6", "Joshua Kimmich", "Bayern Munich", Position.MF, League.BUNDESLIGA),
            p("bl-7", "Alphonso Davies", "Bayern Munich", Position.DF, League.BUNDESLIGA),
            p("bl-8", "Jonathan Tah", "Bayer Leverkusen", Position.DF, League.BUNDESLIGA),
            p("bl-9", "Waldemar Anton", "Borussia Dortmund", Position.DF, League.BUNDESLIGA),
            p("bl-10", "Manuel Neuer", "Bayern Munich", Position.GK, League.BUNDESLIGA),

            // ---------- La Liga ----------
            p("ll-1", "Robert Lewandowski", "Barcelona", Position.FW, League.LA_LIGA),
            p("ll-2", "Kylian Mbappe", "Real Madrid", Position.FW, League.LA_LIGA),
            p("ll-3", "Vinicius Junior", "Real Madrid", Position.FW, League.LA_LIGA),
            p("ll-4", "Antoine Griezmann", "Atletico Madrid", Position.FW, League.LA_LIGA),
            p("ll-5", "Jude Bellingham", "Real Madrid", Position.MF, League.LA_LIGA),
            p("ll-6", "Pedri", "Barcelona", Position.MF, League.LA_LIGA),
            p("ll-7", "Gavi", "Barcelona", Position.MF, League.LA_LIGA),
            p("ll-8", "Ronald Araujo", "Barcelona", Position.DF, League.LA_LIGA),
            p("ll-9", "Antonio Rudiger", "Real Madrid", Position.DF, League.LA_LIGA),
            p("ll-10", "Thibaut Courtois", "Real Madrid", Position.GK, League.LA_LIGA),

            // ---------- Serie A ----------
            p("sa-1", "Lautaro Martinez", "Inter", Position.FW, League.SERIE_A),
            p("sa-2", "Dusan Vlahovic", "Juventus", Position.FW, League.SERIE_A),
            p("sa-3", "Rafael Leao", "Milan", Position.FW, League.SERIE_A),
            p("sa-4", "Khvicha Kvaratskhelia", "Napoli", Position.FW, League.SERIE_A),
            p("sa-5", "Nicolo Barella", "Inter", Position.MF, League.SERIE_A),
            p("sa-6", "Paulo Dybala", "Roma", Position.MF, League.SERIE_A),
            p("sa-7", "Federico Dimarco", "Inter", Position.DF, League.SERIE_A),
            p("sa-8", "Alessandro Bastoni", "Inter", Position.DF, League.SERIE_A),
            p("sa-9", "Bremer", "Juventus", Position.DF, League.SERIE_A),
            p("sa-10", "Mike Maignan", "Milan", Position.GK, League.SERIE_A),

            // ---------- Ligue 1 ----------
            p("l1-1", "Ousmane Dembele", "Paris Saint-Germain", Position.FW, League.LIGUE_1),
            p("l1-2", "Bradley Barcola", "Paris Saint-Germain", Position.FW, League.LIGUE_1),
            p("l1-3", "Jonathan David", "Lille", Position.FW, League.LIGUE_1),
            p("l1-4", "Alexandre Lacazette", "Lyon", Position.FW, League.LIGUE_1),
            p("l1-5", "Vitinha", "Paris Saint-Germain", Position.MF, League.LIGUE_1),
            p("l1-6", "Joao Neves", "Paris Saint-Germain", Position.MF, League.LIGUE_1),
            p("l1-7", "Benjamin Andre", "Lille", Position.MF, League.LIGUE_1),
            p("l1-8", "Marquinhos", "Paris Saint-Germain", Position.DF, League.LIGUE_1),
            p("l1-9", "Nuno Mendes", "Paris Saint-Germain", Position.DF, League.LIGUE_1),
            p("l1-10", "Gianluigi Donnarumma", "Paris Saint-Germain", Position.GK, League.LIGUE_1)
    );

    private static ScrapedPlayer p(String id, String name, String team, Position position, League league) {
        return new ScrapedPlayer(id, name, team, position, league, "");
    }

    /** Devuelve los jugadores del seed correspondientes a una liga. */
    public static List<ScrapedPlayer> forLeague(League league) {
        return PLAYERS.stream().filter(sp -> sp.league() == league).toList();
    }
}
