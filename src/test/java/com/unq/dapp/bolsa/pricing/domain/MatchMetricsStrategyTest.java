package com.unq.dapp.bolsa.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMetricsStrategyTest {

    @Test
    void deberiaCalcularCotizacionConMetricasAltas() {
        // Given
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        PlayerMetricsSnapshot metrics = crearMetrics(20, 15, BigDecimal.valueOf(8.5), 10);
        PricingContext context = PricingContext.withInitialValue(1.0);

        // When
        Money valor = strategy.calculate(metrics, context);

        // Then
        assertThat(valor.amount()).isGreaterThan(BigDecimal.ONE);
        assertThat(valor.currency()).isEqualTo("CREDITS");
    }

    @Test
    void deberiaCalcularCotizacionConMetricasBajas() {
        // Given
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        PlayerMetricsSnapshot metrics = crearMetrics(2, 1, BigDecimal.valueOf(6.0), 5);
        PricingContext context = PricingContext.withInitialValue(1.0);

        // When
        Money valor = strategy.calculate(metrics, context);

        // Then
        assertThat(valor.amount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(valor.amount()).isLessThan(BigDecimal.valueOf(2.0));
    }

    @Test
    void deberiaRetornarValorInicialSinPartidos() {
        // Given
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        PlayerMetricsSnapshot metrics = crearMetrics(0, 0, null, 0);
        PricingContext context = PricingContext.withInitialValue(1.0);

        // When
        Money valor = strategy.calculate(metrics, context);

        // Then
        assertThat(valor.amount()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void deberiaUsarPesosConfigurables() {
        // Given
        StrategyWeights customWeights = new StrategyWeights(0.5, 0.3, 0.2);
        MatchMetricsStrategy strategy = new MatchMetricsStrategy(customWeights);
        PlayerMetricsSnapshot metrics = crearMetrics(10, 5, BigDecimal.valueOf(7.5), 8);
        PricingContext context = PricingContext.withInitialValue(1.0);

        // When
        Money valor = strategy.calculate(metrics, context);

        // Then
        assertThat(valor.amount()).isGreaterThan(BigDecimal.ONE);
    }

    @Test
    void deberiaNormalizarMetricasCorrectamente() {
        // Given - métricas que exceden los má ximos
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();
        PlayerMetricsSnapshot metrics = crearMetrics(50, 30, BigDecimal.valueOf(12.0), 20);
        PricingContext context = PricingContext.withInitialValue(1.0);

        // When
        Money valor = strategy.calculate(metrics, context);

        // Then - no debería explotar, debe normalizar a 1.0 máximo
        assertThat(valor.amount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(valor.amount()).isLessThanOrEqualTo(BigDecimal.valueOf(3.0));
    }

    @Test
    void deberiaRetornarNombreYVersion() {
        // Given
        MatchMetricsStrategy strategy = new MatchMetricsStrategy();

        // Then
        assertThat(strategy.name()).isEqualTo("MatchMetrics");
        assertThat(strategy.version()).isEqualTo("v1.0");
    }

    private PlayerMetricsSnapshot crearMetrics(int goals, int assists, BigDecimal rating, int matches) {
        PlayerMetricsSnapshot metrics = new PlayerMetricsSnapshot();
        metrics.setPlayerId(1L);
        metrics.setGoals(goals);
        metrics.setAssists(assists);
        metrics.setRating(rating);
        metrics.setMatches(matches);
        metrics.setMinutesPlayed(matches * 90);
        metrics.setPeriodStart(LocalDate.now().minusMonths(1));
        metrics.setPeriodEnd(LocalDate.now());
        return metrics;
    }
}

