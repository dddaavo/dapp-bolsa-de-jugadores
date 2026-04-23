package com.unq.dapp.bolsa.catalog.api;

import com.unq.dapp.bolsa.catalog.application.PlayerService;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Catálogo", description = "Jugadores de las 5 grandes ligas europeas")
@SecurityRequirement(name = "bearerAuth")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    @Operation(summary = "Listar jugadores con filtros opcionales y paginación")
    public ResponseEntity<Page<PlayerResponse>> list(
            @Parameter(description = "Filtrar por liga") @RequestParam(required = false) League league,
            @Parameter(description = "Filtrar por equipo (parcial)") @RequestParam(required = false) String team,
            @Parameter(description = "Filtrar por posición") @RequestParam(required = false) Position position,
            Pageable pageable
    ) {
        return ResponseEntity.ok(playerService.list(league, team, position, pageable));
    }


}
