package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback con datos estáticos cuando WhoScored no es alcanzable o el scraping falla.
 * Contiene los mismos jugadores que tenía el DataInitializer original,
 * garantizando que la aplicación siempre arranque con datos coherentes.
 */
@Component
public class WhoScoredFixturesFallback implements PlayerStatsPort {

    @Override
    public List<ScrapedPlayer> fetchPlayersByLeague(League league) {
        return switch (league) {
            case PREMIER_LEAGUE -> premierLeague();
            case BUNDESLIGA     -> bundesliga();
            case LA_LIGA        -> laLiga();
            case SERIE_A        -> serieA();
            case LIGUE_1        -> ligue1();
        };
    }

    private List<ScrapedPlayer> premierLeague() {
        return List.of(
                p("ws-pl-1", "Erling Haaland",   "Manchester City", Position.FW, League.PREMIER_LEAGUE, "Norwegian"),
                p("ws-pl-2", "Mohamed Salah",     "Liverpool",       Position.FW, League.PREMIER_LEAGUE, "Egyptian"),
                p("ws-pl-3", "Phil Foden",        "Manchester City", Position.MF, League.PREMIER_LEAGUE, "English"),
                p("ws-pl-4", "Virgil van Dijk",   "Liverpool",       Position.DF, League.PREMIER_LEAGUE, "Dutch"),
                p("ws-pl-5", "David Raya",        "Arsenal",         Position.GK, League.PREMIER_LEAGUE, "Spanish")
        );
    }

    private List<ScrapedPlayer> bundesliga() {
        return List.of(
                p("ws-bl-1", "Harry Kane",          "Bayern Munich",     Position.FW, League.BUNDESLIGA, "English"),
                p("ws-bl-2", "Florian Wirtz",       "Bayer Leverkusen",  Position.MF, League.BUNDESLIGA, "German"),
                p("ws-bl-3", "Joshua Kimmich",      "Bayern Munich",     Position.MF, League.BUNDESLIGA, "German"),
                p("ws-bl-4", "Nico Schlotterbeck",  "Borussia Dortmund", Position.DF, League.BUNDESLIGA, "German"),
                p("ws-bl-5", "Manuel Neuer",        "Bayern Munich",     Position.GK, League.BUNDESLIGA, "German")
        );
    }

    private List<ScrapedPlayer> laLiga() {
        return List.of(
                p("ws-ll-1", "Vinícius Júnior",    "Real Madrid",  Position.FW, League.LA_LIGA, "Brazilian"),
                p("ws-ll-2", "Robert Lewandowski", "FC Barcelona", Position.FW, League.LA_LIGA, "Polish"),
                p("ws-ll-3", "Jude Bellingham",    "Real Madrid",  Position.MF, League.LA_LIGA, "English"),
                p("ws-ll-4", "Pedri",              "FC Barcelona", Position.MF, League.LA_LIGA, "Spanish"),
                p("ws-ll-5", "Ter Stegen",         "FC Barcelona", Position.GK, League.LA_LIGA, "German")
        );
    }

    private List<ScrapedPlayer> serieA() {
        return List.of(
                p("ws-sa-1", "Lautaro Martínez",    "Inter Milan", Position.FW, League.SERIE_A, "Argentine"),
                p("ws-sa-2", "Nicolò Barella",      "Inter Milan", Position.MF, League.SERIE_A, "Italian"),
                p("ws-sa-3", "Alessandro Bastoni",  "Inter Milan", Position.DF, League.SERIE_A, "Italian"),
                p("ws-sa-4", "Federico Gatti",      "Juventus",    Position.DF, League.SERIE_A, "Italian"),
                p("ws-sa-5", "Mike Maignan",        "AC Milan",    Position.GK, League.SERIE_A, "French")
        );
    }

    private List<ScrapedPlayer> ligue1() {
        return List.of(
                p("ws-l1-1", "Jonathan David",       "LOSC Lille",           Position.FW, League.LIGUE_1, "Canadian"),
                p("ws-l1-2", "Bradley Barcola",      "Paris Saint-Germain",  Position.FW, League.LIGUE_1, "French"),
                p("ws-l1-3", "Warren Zaïre-Emery",   "Paris Saint-Germain",  Position.MF, League.LIGUE_1, "French"),
                p("ws-l1-4", "Désiré Doué",          "Paris Saint-Germain",  Position.MF, League.LIGUE_1, "French"),
                p("ws-l1-5", "Gianluigi Donnarumma", "Paris Saint-Germain",  Position.GK, League.LIGUE_1, "Italian")
        );
    }

    private ScrapedPlayer p(String id, String name, String team, Position pos, League league, String nat) {
        return new ScrapedPlayer(id, name, team, pos, league, nat);
    }
}

