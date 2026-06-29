package com.unq.dapp.bolsa.trading.domain;

import com.unq.dapp.bolsa.shared.error.DomainException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "token_holdings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "playerId"}))
public class TokenHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal avgBuyPrice;

    @Version
    private Long version;

    public static TokenHolding createNew(Long userId, Long playerId, int quantity, BigDecimal unitPrice) {
        TokenHolding h = new TokenHolding();
        h.userId = userId;
        h.playerId = playerId;
        h.quantity = quantity;
        h.avgBuyPrice = unitPrice;
        return h;
    }

    public void addPurchase(BigDecimal unitPrice, int qty) {
        BigDecimal totalCost = avgBuyPrice.multiply(BigDecimal.valueOf(quantity))
                .add(unitPrice.multiply(BigDecimal.valueOf(qty)));
        quantity += qty;
        avgBuyPrice = totalCost.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP);
    }

    public void sell(int qty) {
        if (quantity < qty) {
            throw new DomainException("INSUFFICIENT_HOLDING",
                    "Holding insuficiente: disponible " + quantity + ", solicitado " + qty);
        }
        quantity -= qty;
    }

    public boolean isSoldOut() {
        return quantity == 0;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getAvgBuyPrice() { return avgBuyPrice; }
    public void setAvgBuyPrice(BigDecimal avgBuyPrice) { this.avgBuyPrice = avgBuyPrice; }
    public Long getVersion() { return version; }
}
