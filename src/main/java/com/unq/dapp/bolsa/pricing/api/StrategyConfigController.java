package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.pricing.application.StrategyConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategies")
@Tag(name = "Estrategias", description = "Configuración de pesos de estrategias de cotización")
public class StrategyConfigController {

    private final StrategyConfigService strategyConfigService;

    public StrategyConfigController(StrategyConfigService strategyConfigService) {
        this.strategyConfigService = strategyConfigService;
    }

    @GetMapping
    @Operation(summary = "Lista las configuraciones activas de estrategias")
    public ResponseEntity<List<StrategyConfigResponse>> getAll() {
        return ResponseEntity.ok(strategyConfigService.getAll());
    }

    @PutMapping("/{name}/config")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualiza los pesos de una estrategia (ADMIN)")
    @ApiResponse(responseCode = "400", description = "weightsJson inválido")
    @ApiResponse(responseCode = "403", description = "Sin permisos (requiere ADMIN)")
    public ResponseEntity<StrategyConfigResponse> updateWeights(
            @PathVariable String name,
            @Valid @RequestBody UpdateStrategyWeightsRequest request) {
        return ResponseEntity.ok(strategyConfigService.updateWeights(name, request));
    }
}
