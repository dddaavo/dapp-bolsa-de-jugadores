package com.unq.dapp.bolsa.pricing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingContextTest {

    @Test
    void deberiaCrearContextoConValorInicial() {
        // When
        PricingContext context = new PricingContext(Money.of(1.5), null);

        // Then
        assertThat(context.initialTokenValue().amount()).isEqualByComparingTo(Money.of(1.5).amount());
    }

    @Test
    void deberiaCrearContextoConValorDouble() {
        // When
        PricingContext context = PricingContext.withInitialValue(2.0);

        // Then
        assertThat(context.initialTokenValue().amount()).isEqualByComparingTo(Money.of(2.0).amount());
        assertThat(context.initialTokenValue().currency()).isEqualTo("CREDITS");
    }

    @Test
    void deberianSerIgualesDosContextosConMismoValor() {
        // Given
        PricingContext context1 = PricingContext.withInitialValue(1.0);
        PricingContext context2 = new PricingContext(Money.of(1.0), null);

        // Then
        assertThat(context1).isEqualTo(context2);
    }
}

