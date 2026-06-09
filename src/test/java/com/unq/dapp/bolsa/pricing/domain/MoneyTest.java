package com.unq.dapp.bolsa.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void deberiaCrearMoneyConValorPositivo() {
        // When
        Money money = Money.of(10.5);

        // Then
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.valueOf(10.5));
        assertThat(money.currency()).isEqualTo("CREDITS");
    }

    @Test
    void deberiaCrearMoneyConBigDecimal() {
        // When
        Money money = Money.of(BigDecimal.valueOf(25.75));

        // Then
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.valueOf(25.75));
    }

    @Test
    void deberiaCrearMoneyCero() {
        // When
        Money zero = Money.zero();

        // Then
        assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(zero.currency()).isEqualTo("CREDITS");
    }

    @Test
    void deberiaLanzarExcepcionConAmountNegativo() {
        // When/Then
        BigDecimal negativeAmount = BigDecimal.valueOf(-5);
        assertThatThrownBy(() -> new Money(negativeAmount, "CREDITS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void deberiaLanzarExcepcionConAmountNull() {
        // When/Then
        assertThatThrownBy(() -> new Money(null, "CREDITS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void deberiaLanzarExcepcionConCurrencyNull() {
        // When/Then
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void deberiaLanzarExcepcionConCurrencyVacia() {
        // When/Then
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void deberiaSumarDosMoneys() {
        // Given
        Money money1 = Money.of(10.5);
        Money money2 = Money.of(5.25);

        // When
        Money resultado = money1.add(money2);

        // Then
        assertThat(resultado.amount()).isEqualByComparingTo(BigDecimal.valueOf(15.75));
    }

    @Test
    void deberiaLanzarExcepcionAlSumarDiferentesMonedas() {
        // Given
        Money credits = new Money(BigDecimal.TEN, "CREDITS");
        Money dollars = new Money(BigDecimal.TEN, "USD");

        // When/Then
        assertThatThrownBy(() -> credits.add(dollars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different currencies");
    }

    @Test
    void deberiaMultiplicarPorEscalar() {
        // Given
        Money money = Money.of(10.0);

        // When
        Money resultado = money.multiply(1.5);

        // Then
        assertThat(resultado.amount()).isEqualByComparingTo(BigDecimal.valueOf(15.0));
    }

    @Test
    void deberiaMultiplicarPorCero() {
        // Given
        Money money = Money.of(100.0);

        // When
        Money resultado = money.multiply(0);

        // Then
        assertThat(resultado.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deberiaRedondearA2DecimalesAlCrear() {
        // When - usar el método of() que aplica setScale
        Money money = Money.of(BigDecimal.valueOf(10.123456));

        // Then
        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.valueOf(10.12));
    }

    @Test
    void deberiaGenerarToStringCorrectamente() {
        // Given
        Money money = Money.of(42.50);

        // When
        String resultado = money.toString();

        // Then
        assertThat(resultado).isEqualTo("42.50 CREDITS");
    }

    @Test
    void deberianSerIgualesDosMoneyConMismoValor() {
        // Given
        Money money1 = Money.of(10.5);
        Money money2 = Money.of(10.5);

        // Then
        assertThat(money1).isEqualTo(money2);
    }
}

