package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.shared.error.DomainException;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "player_token_inventory")
public class PlayerTokenInventory {

    @Id
    private Long playerId;

    @Column(nullable = false)
    private Integer totalEmitted = 100;

    @Column(nullable = false)
    private Integer heldBySystem = 100;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal initialTokenValue = BigDecimal.valueOf(1.0);

    @Version
    private Long version;

    public void reserve(int quantity) {
        if (heldBySystem < quantity) {
            throw new DomainException("INSUFFICIENT_STOCK",
                    "Stock insuficiente: disponible " + heldBySystem + ", solicitado " + quantity);
        }
        heldBySystem -= quantity;
    }

    public void release(int quantity) {
        heldBySystem += quantity;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Integer getTotalEmitted() {
        return totalEmitted;
    }

    public void setTotalEmitted(Integer totalEmitted) {
        this.totalEmitted = totalEmitted;
    }

    public Integer getHeldBySystem() {
        return heldBySystem;
    }

    public void setHeldBySystem(Integer heldBySystem) {
        this.heldBySystem = heldBySystem;
    }

    public BigDecimal getInitialTokenValue() {
        return initialTokenValue;
    }

    public void setInitialTokenValue(BigDecimal initialTokenValue) {
        this.initialTokenValue = initialTokenValue;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

