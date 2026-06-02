package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.pricing.domain.MatchMetricsStrategy;
import com.unq.dapp.bolsa.pricing.domain.PricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryTest {

    private StrategyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StrategyRegistry();
    }

    @Test
    void deberiaRegistrarEstrategia() {
        // Given
        PricingStrategy strategy = new MatchMetricsStrategy();

        // When
        registry.register(strategy);

        // Then
        assertThat(registry.get("MatchMetrics")).isPresent();
        assertThat(registry.exists("MatchMetrics")).isTrue();
    }

    @Test
    void deberiaEstablecerEstrategiaPorDefecto() {
        // Given
        PricingStrategy strategy = new MatchMetricsStrategy();

        // When
        registry.setDefaultStrategy(strategy);

        // Then
        assertThat(registry.getDefault()).isEqualTo(strategy);
    }

    @Test
    void deberiaRetornarEmptySiEstrategiaNoExiste() {
        // When/Then
        assertThat(registry.get("NonExistent")).isEmpty();
        assertThat(registry.exists("NonExistent")).isFalse();
    }

    @Test
    void deberiaLanzarExcepcionSiNoHayEstrategiaPorDefecto() {
        // When/Then
        assertThatThrownBy(() -> registry.getDefault())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No default pricing strategy");
    }

    @Test
    void setDefaultStrategyDeberiaRegistrarLaEstrategia() {
        // Given
        PricingStrategy strategy = new MatchMetricsStrategy();

        // When
        registry.setDefaultStrategy(strategy);

        // Then
        assertThat(registry.exists("MatchMetrics")).isTrue();
        assertThat(registry.get("MatchMetrics")).isPresent();
    }
}

