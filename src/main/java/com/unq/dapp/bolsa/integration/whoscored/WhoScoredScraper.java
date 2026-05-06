package com.unq.dapp.bolsa.integration.whoscored;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;

import java.util.List;

public interface WhoScoredScraper {
    List<ScrapedPlayer> fetchPlayersByLeague(League league);
}
