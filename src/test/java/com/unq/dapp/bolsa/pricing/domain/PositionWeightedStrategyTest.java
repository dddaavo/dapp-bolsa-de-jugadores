package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.catalog.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class PositionWeightedStrategyTest {

    private final PositionWeightedStrategy strategy = new PositionWeightedStrategy();

    @Test
    void deberiaRetornarNombreYVersion() {
        assertThat(strategy.name()).isEqualTo("PositionMetrics");
        assertThat(strategy.version()).isEqualTo("v1.0");
    }

    @Test
    void deberiaRetornarValorInicialSinPartidos() {
        PlayerMetricsSnapshot metrics = crearMetrics(10, 5, BigDecimal.valueOf(8.0), 0);
        PricingContext context = PricingContext.withInitialValue(1.0, Position.FW);

        Money valor = strategy.calculate(metrics, context);

        assertThat(valor.amount()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void delanteroPriorizaGoles() {
        PlayerMetricsSnapshot metricasDelantero = crearMetrics(20, 2, BigDecimal.valueOf(6.5), 10);
        PlayerMetricsSnapshot metricasMediocampista = crearMetrics(20, 2, BigDecimal.valueOf(6.5), 10);

        Money valorFW = strategy.calculate(metricasDelantero, PricingContext.withInitialValue(1.0, Position.FW));
        Money valorMF = strategy.calculate(metricasMediocampista, PricingContext.withInitialValue(1.0, Position.MF));

        // FW le da más peso a goles (0.6) que MF (0.2), así que con muchos goles FW > MF
        assertThat(valorFW.amount()).isGreaterThan(valorMF.amount());
    }

    @Test
    void mediocampistaBalanceaAsistencias() {
        PlayerMetricsSnapshot metrics = crearMetrics(2, 18, BigDecimal.valueOf(7.0), 10);

        Money valorMF = strategy.calculate(metrics, PricingContext.withInitialValue(1.0, Position.MF));
        Money valorFW = strategy.calculate(metrics, PricingContext.withInitialValue(1.0, Position.FW));

        // MF pesa asistencias 0.5 vs FW 0.3, con muchas asistencias MF > FW
        assertThat(valorMF.amount()).isGreaterThan(valorFW.amount());
    }

    @Test
    void defensorPriorizaRating() {
        PlayerMetricsSnapshot metrics = crearMetrics(1, 2, BigDecimal.valueOf(9.5), 10);

        Money valorDF = strategy.calculate(metrics, PricingContext.withInitialValue(1.0, Position.DF));
        Money valorFW = strategy.calculate(metrics, PricingContext.withInitialValue(1.0, Position.FW));

        // DF pesa rating 0.7 vs FW 0.1, con rating alto DF > FW
        assertThat(valorDF.amount()).isGreaterThan(valorFW.amount());
    }

    @Test
    void arqueroSoloUsaRating() {
        PlayerMetricsSnapshot metrics = crearMetrics(0, 0, BigDecimal.valueOf(8.0), 10);
        PricingContext context = PricingContext.withInitialValue(1.0, Position.GK);

        Money valor = strategy.calculate(metrics, context);

        // GK con rating=8.0/10.0=0.8 → score=0.8 → value=1.8
        assertThat(valor.amount()).isGreaterThan(BigDecimal.ONE);
        assertThat(valor.amount()).isEqualByComparingTo(BigDecimal.valueOf(1.8));
    }

    @Test
    void posicionNullUsaPesosBalanceados() {
        PlayerMetricsSnapshot metrics = crearMetrics(5, 5, BigDecimal.valueOf(7.0), 10);
        PricingContext contextoSinPosicion = PricingContext.withInitialValue(1.0, null);
        PricingContext contextoMF = PricingContext.withInitialValue(1.0, Position.MF);

        Money valorNull = strategy.calculate(metrics, contextoSinPosicion);
        Money valorMF = strategy.calculate(metrics, contextoMF);

        assertThat(valorNull.amount()).isEqualByComparingTo(valorMF.amount());
    }

    @ParameterizedTest
    @EnumSource(Position.class)
    void deberiaProducirValorPositivoParaCualquierPosicion(Position position) {
        PlayerMetricsSnapshot metrics = crearMetrics(5, 5, BigDecimal.valueOf(7.0), 10);
        PricingContext context = PricingContext.withInitialValue(1.0, position);

        Money valor = strategy.calculate(metrics, context);

        assertThat(valor.amount()).isGreaterThan(BigDecimal.ZERO);
    }

    private PlayerMetricsSnapshot crearMetrics(int goals, int assists, BigDecimal rating, int matches) {
        PlayerMetricsSnapshot m = new PlayerMetricsSnapshot();
        m.setPlayerId(1L);
        m.setGoals(goals);
        m.setAssists(assists);
        m.setRating(rating);
        m.setMatches(matches);
        m.setMinutesPlayed(matches * 90);
        m.setPeriodStart(LocalDate.of(2026, Month.JANUARY, 1));
        m.setPeriodEnd(LocalDate.of(2026, Month.JUNE, 1));
        return m;
    }
}
