package com.unq.dapp.bolsa.catalog.api;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;

public record PlayerResponse(
        Long id,
        String name,
        Position position,
        String team,
        League league,
        String nationality
) {
    public static PlayerResponse from(Player p) {
        return new PlayerResponse(
                p.getId(),
                p.getName(),
                p.getPosition(),
                p.getTeam(),
                p.getLeague(),
                p.getNationality()
        );
    }
}
