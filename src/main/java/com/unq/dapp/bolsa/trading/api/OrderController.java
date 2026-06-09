package com.unq.dapp.bolsa.trading.api;

import com.unq.dapp.bolsa.auth.domain.User;
import com.unq.dapp.bolsa.shared.error.DomainException;
import com.unq.dapp.bolsa.trading.application.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mercado", description = "Compra y venta de tokens de jugadores")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Comprar tokens de un jugador")
    @ApiResponse(responseCode = "201", description = "Compra ejecutada")
    @ApiResponse(responseCode = "400", description = "Request inválido o falta Idempotency-Key")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "409", description = "Idempotency-Key ya usada con request diferente")
    @ApiResponse(responseCode = "422", description = "Sin stock o sin cotización vigente")
    @PostMapping("/buy")
    public ResponseEntity<OrderResponse> buy(
            @AuthenticationPrincipal User currentUser,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BuyRequest request) {

        validateIdempotencyKey(idempotencyKey);
        OrderResponse response = orderService.buy(currentUser.getId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Vender tokens de un jugador")
    @ApiResponse(responseCode = "201", description = "Venta ejecutada")
    @ApiResponse(responseCode = "400", description = "Request inválido o falta Idempotency-Key")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "409", description = "Idempotency-Key ya usada con request diferente")
    @ApiResponse(responseCode = "422", description = "Sin holding suficiente o sin cotización vigente")
    @PostMapping("/sell")
    public ResponseEntity<OrderResponse> sell(
            @AuthenticationPrincipal User currentUser,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SellRequest request) {

        validateIdempotencyKey(idempotencyKey);
        OrderResponse response = orderService.sell(currentUser.getId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new DomainException("MISSING_IDEMPOTENCY_KEY",
                    "Se requiere el header Idempotency-Key", HttpStatus.BAD_REQUEST);
        }
    }
}
