package com.unq.dapp.bolsa.pricing.domain;

/**
 * Contexto que se pasa a las estrategias de pricing.
 * Contiene información adicional necesaria para el cálculo.
 */
public record PricingContext(Money initialTokenValue) {

    public static PricingContext withInitialValue(double value) {
        return new PricingContext(Money.of(value));
    }
}

