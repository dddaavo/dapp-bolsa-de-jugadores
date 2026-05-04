package com.unq.dapp.bolsa.catalog.api;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlayerResponse(
        @Schema(description = "ID del jugador", example = "1") Long id,
        @Schema(description = "Nombre completo", example = "Erling Haaland") String name,
        @Schema(description = "Posición en el campo") Position position,
        @Schema(description = "Equipo actual", example = "Manchester City") String team,
        @Schema(description = "Liga") League league,
        @Schema(description = "Nacionalidad", example = "Norwegian") String nationality
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
