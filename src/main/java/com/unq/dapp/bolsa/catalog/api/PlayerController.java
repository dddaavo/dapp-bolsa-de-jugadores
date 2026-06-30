package com.unq.dapp.bolsa.catalog.api;

import com.unq.dapp.bolsa.catalog.application.PlayerService;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Catálogo", description = "Jugadores de las 5 grandes ligas europeas")
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Operation(summary = "Listar jugadores con filtros opcionales y paginación")
    @ApiResponse(responseCode = "200", description = "Listado paginado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping
    public ResponseEntity<Page<PlayerResponse>> list(
            @Parameter(description = "Filtrar por liga") @RequestParam(required = false) League league,
            @Parameter(description = "Filtrar por equipo (coincidencia parcial)") @RequestParam(required = false) String team,
            @Parameter(description = "Filtrar por posición") @RequestParam(required = false) Position position,
            @Parameter(description = "Filtrar por nombre (coincidencia parcial)") @RequestParam(required = false) String name,
            Pageable pageable
    ) {
        return ResponseEntity.ok(playerService.list(league, team, position, name, pageable).map(PlayerResponse::from));
    }

    @Operation(summary = "Obtener jugador por ID")
    @ApiResponse(responseCode = "200", description = "Jugador encontrado")
    @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(PlayerResponse.from(playerService.findById(id)));
    }
}
