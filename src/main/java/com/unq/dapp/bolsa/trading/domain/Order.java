package com.unq.dapp.bolsa.trading.domain;

import com.unq.dapp.bolsa.shared.audit.AuditableEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class Order extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    public static Order createBuy(Long userId, Long playerId, int quantity,
                                   BigDecimal unitPrice, String idempotencyKey) {
        return create(userId, playerId, OrderType.BUY, quantity, unitPrice, idempotencyKey);
    }

    public static Order createSell(Long userId, Long playerId, int quantity,
                                    BigDecimal unitPrice, String idempotencyKey) {
        return create(userId, playerId, OrderType.SELL, quantity, unitPrice, idempotencyKey);
    }

    private static Order create(Long userId, Long playerId, OrderType type, int quantity,
                                 BigDecimal unitPrice, String idempotencyKey) {
        Order o = new Order();
        o.userId = userId;
        o.playerId = playerId;
        o.type = type;
        o.quantity = quantity;
        o.unitPrice = unitPrice;
        o.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        o.idempotencyKey = idempotencyKey;
        return o;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
