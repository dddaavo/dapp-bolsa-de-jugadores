package com.unq.dapp.bolsa.trading.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

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
