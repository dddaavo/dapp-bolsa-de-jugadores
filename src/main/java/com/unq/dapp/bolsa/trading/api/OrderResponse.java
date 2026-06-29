package com.unq.dapp.bolsa.trading.api;

import com.unq.dapp.bolsa.trading.domain.Order;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        @Schema(description = "ID de la orden") Long orderId,
        @Schema(description = "ID del jugador") Long playerId,
        @Schema(description = "Tipo de operación") OrderType type,
        @Schema(description = "Cantidad de tokens") Integer quantity,
        @Schema(description = "Precio unitario al momento de la operación") BigDecimal unitPrice,
        @Schema(description = "Monto total") BigDecimal totalAmount,
        @Schema(description = "Fecha y hora de ejecución (ISO-8601)") Instant executedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getPlayerId(),
                order.getType(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
