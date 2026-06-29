package com.unq.dapp.bolsa.metrics.api;

import com.unq.dapp.bolsa.metrics.application.MarketMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Métricas", description = "Estadísticas agregadas del mercado de tokens")
@RestController
@RequestMapping("/api/v1/metrics")
public class MarketMetricsController {

    private final MarketMetricsService metricsService;

    public MarketMetricsController(MarketMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Operation(summary = "Métricas de mercado",
               description = "Devuelve estadísticas agregadas: volumen de órdenes, capitalización, top jugadores operados y top variaciones de cotización")
    @ApiResponse(responseCode = "200", description = "Métricas calculadas")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping("/market")
    public ResponseEntity<MarketMetricsResponse> getMarketMetrics() {
        return ResponseEntity.ok(metricsService.getMarketMetrics());
    }
}
