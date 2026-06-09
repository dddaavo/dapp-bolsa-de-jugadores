package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.pricing.application.QuoteRecalculationOrchestrator;
import com.unq.dapp.bolsa.pricing.application.StrategyRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Tag(name = "Cotizaciones", description = "Cotizaciones de tokens de jugadores")
@RestController
@RequestMapping("/api/v1/quotes")
public class RecalculationController {

    private final QuoteRecalculationOrchestrator orchestrator;
    private final StrategyRegistry strategyRegistry;

    public RecalculationController(QuoteRecalculationOrchestrator orchestrator, StrategyRegistry strategyRegistry) {
        this.orchestrator = orchestrator;
        this.strategyRegistry = strategyRegistry;
    }

    @Operation(summary = "Recalcular cotizaciones de todos los jugadores (solo ADMIN)")
    @ApiResponse(responseCode = "200", description = "Recalculación completada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Sin permisos (requiere ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recalculate")
    public ResponseEntity<RecalculationResponse> recalculate(@RequestBody(required = false) RecalculateRequest request) {
        String strategyName = (request != null) ? request.strategyName() : null;
        String resolvedStrategy = (strategyName != null && !strategyName.isBlank())
                ? strategyName
                : strategyRegistry.getDefault().name();

        int total = orchestrator.recalculateAll(strategyName);

        return ResponseEntity.ok(new RecalculationResponse(total, resolvedStrategy, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
