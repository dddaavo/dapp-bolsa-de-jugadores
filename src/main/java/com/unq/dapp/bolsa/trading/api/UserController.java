package com.unq.dapp.bolsa.trading.api;

import com.unq.dapp.bolsa.trading.application.OrderService;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Usuarios", description = "Operaciones sobre el usuario autenticado")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final OrderService orderService;

    public UserController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Historial de órdenes del usuario")
    @ApiResponse(responseCode = "200", description = "Historial de órdenes")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Sin permisos para ver este usuario")
    @GetMapping("/{id}/transactions")
    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable Long id,
            @Parameter(description = "Filtrar por tipo: BUY o SELL")
            @RequestParam(required = false) OrderType type,
            @Parameter(description = "Fecha inicio (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha fin (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(orderService.getTransactions(id, type, from, to, pageable));
    }
}
