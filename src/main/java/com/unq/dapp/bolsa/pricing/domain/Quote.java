package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.shared.audit.AuditableEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cotización de un jugador en un momento determinado.
 * Incluye auditoría de qué estrategia y versión se usó para calcularla.
 */
@Entity
@Table(name = "quote", indexes = {
    @Index(name = "idx_quote_player_calculated", columnList = "playerId,calculatedAt")
})
public class Quote extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valueAmount;

    @Column(nullable = false, length = 10)
    private String valueCurrency;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @Column(nullable = false, length = 100)
    private String strategyName;

    @Column(nullable = false, length = 20)
    private String strategyVersion;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Money getValue() {
        return new Money(valueAmount, valueCurrency);
    }

    public void setValue(Money value) {
        this.valueAmount = value.amount();
        this.valueCurrency = value.currency();
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public void setStrategyVersion(String strategyVersion) {
        this.strategyVersion = strategyVersion;
    }
}

