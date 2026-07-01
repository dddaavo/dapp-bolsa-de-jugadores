package com.unq.dapp.bolsa.trading.api;

import com.unq.dapp.bolsa.trading.domain.Order;
import com.unq.dapp.bolsa.trading.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long orderId,
        Long playerId,
        String playerName,
        OrderType type,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        Instant executedAt
) {
    public static OrderResponse from(Order order, String playerName) {
        return new OrderResponse(
                order.getId(),
                order.getPlayerId(),
                playerName,
                order.getType(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
