package com.unq.dapp.bolsa.pricing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyWeightsTest {

    @Test
    void deberiaCrearPesosValidos() {
        // When
        StrategyWeights weights = new StrategyWeights(0.4, 0.3, 0.3);

        // Then
        assertThat(weights.goals()).isEqualTo(0.4);
        assertThat(weights.assists()).isEqualTo(0.3);
        assertThat(weights.rating()).isEqualTo(0.3);
    }

    @Test
    void deberiaCrearPesosPorDefecto() {
        // When
        StrategyWeights weights = StrategyWeights.defaultWeights();

        // Then
        assertThat(weights.goals()).isEqualTo(0.4);
        assertThat(weights.assists()).isEqualTo(0.3);
        assertThat(weights.rating()).isEqualTo(0.3);
    }

    @Test
    void deberiaLanzarExcepcionConPesoNegativo() {
        // When/Then
        assertThatThrownBy(() -> new StrategyWeights(-0.1, 0.6, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void deberiaLanzarExcepcionCuandoNoSumanUno() {
        // When/Then
        assertThatThrownBy(() -> new StrategyWeights(0.5, 0.3, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must sum to 1.0");
    }

    @Test
    void deberiaPermitirPequenaToleranciaEnLaSuma() {
        // When - suma 1.009 (dentro de tolerancia 0.01)
        StrategyWeights weights = new StrategyWeights(0.333, 0.333, 0.343);

        // Then
        assertThat(weights).isNotNull();
    }

    @Test
    void deberianSerIgualesDosWeightsConMismosValores() {
        // Given
        StrategyWeights weights1 = new StrategyWeights(0.5, 0.25, 0.25);
        StrategyWeights weights2 = new StrategyWeights(0.5, 0.25, 0.25);

        // Then
        assertThat(weights1).isEqualTo(weights2);
    }
}

