package com.unq.dapp.bolsa.portfolio.api;

import com.unq.dapp.bolsa.portfolio.application.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Usuarios", description = "Operaciones sobre el usuario autenticado")
@RestController
@RequestMapping("/api/v1/users")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Operation(summary = "Portfolio del usuario: posiciones, valorización y ganancia/pérdida")
    @ApiResponse(responseCode = "200", description = "Portfolio obtenido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Sin permisos para ver este usuario")
    @GetMapping("/{id}/portfolio")
    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable Long id) {
        return ResponseEntity.ok(portfolioService.getPortfolio(id));
    }
}
