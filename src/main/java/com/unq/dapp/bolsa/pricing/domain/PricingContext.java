package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.catalog.domain.Position;

public record PricingContext(Money initialTokenValue, Position position) {

    public static PricingContext withInitialValue(double value) {
        return new PricingContext(Money.of(value), null);
    }

    public static PricingContext withInitialValue(double value, Position position) {
        return new PricingContext(Money.of(value), position);
    }
}

