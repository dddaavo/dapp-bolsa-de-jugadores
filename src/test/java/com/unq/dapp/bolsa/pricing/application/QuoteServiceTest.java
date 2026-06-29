package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteRepository quoteRepository;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(quoteRepository);
    }

    @Test
    void deberiaObtenerCotizacionActual() {
        // Given
        Long playerId = 1L;
        Quote quote = crearQuote(playerId, Money.of(1.5));
        when(quoteRepository.findTopByPlayerIdOrderByCalculatedAtDesc(playerId))
                .thenReturn(Optional.of(quote));

        // When
        Optional<Quote> resultado = quoteService.getCurrentQuote(playerId);

        // Then
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getPlayerId()).isEqualTo(playerId);
        verify(quoteRepository).findTopByPlayerIdOrderByCalculatedAtDesc(playerId);
    }

    @Test
    void deberiaRetornarEmptySinCotizacion() {
        // Given
        Long playerId = 999L;
        when(quoteRepository.findTopByPlayerIdOrderByCalculatedAtDesc(playerId))
                .thenReturn(Optional.empty());

        // When
        Optional<Quote> resultado = quoteService.getCurrentQuote(playerId);

        // Then
        assertThat(resultado).isEmpty();
    }

    @Test
    void deberiaObtenerHistorialEnRangoDeFechas() {
        // Given
        Long playerId = 1L;
        LocalDate from = LocalDate.of(2026, Month.JANUARY, 8);
        LocalDate to = LocalDate.of(2026, Month.JANUARY, 15);

        Quote quote1 = crearQuote(playerId, Money.of(1.5));
        Quote quote2 = crearQuote(playerId, Money.of(1.6));
        List<Quote> quotes = Arrays.asList(quote1, quote2);

        when(quoteRepository.findByPlayerIdAndCalculatedAtBetweenOrderByCalculatedAtDesc(
                eq(playerId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(quotes);

        // When
        List<Quote> resultado = quoteService.getQuoteHistory(playerId, from, to);

        // Then
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getPlayerId()).isEqualTo(playerId);
    }

    @Test
    void deberiaRetornarHistorialVacioSinCotizaciones() {
        // Given
        Long playerId = 1L;
        LocalDate from = LocalDate.of(2026, Month.JANUARY, 8);
        LocalDate to = LocalDate.of(2026, Month.JANUARY, 15);

        when(quoteRepository.findByPlayerIdAndCalculatedAtBetweenOrderByCalculatedAtDesc(
                eq(playerId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<Quote> resultado = quoteService.getQuoteHistory(playerId, from, to);

        // Then
        assertThat(resultado).isEmpty();
    }

    @Test
    void deberiaObtenerTodasLasCotizaciones() {
        // Given
        Long playerId = 1L;
        List<Quote> quotes = Arrays.asList(
                crearQuote(playerId, Money.of(1.5)),
                crearQuote(playerId, Money.of(1.6)),
                crearQuote(playerId, Money.of(1.7))
        );
        when(quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(playerId))
                .thenReturn(quotes);

        // When
        List<Quote> resultado = quoteService.getAllQuotes(playerId);

        // Then
        assertThat(resultado).hasSize(3);
    }

    @Test
    void deberiaObtenerCotizacionVigenteAUnaFecha() {
        // Given
        Long playerId = 1L;
        LocalDate date = LocalDate.of(2026, Month.JUNE, 15);
        Quote quote = crearQuote(playerId, Money.of(2.0));
        when(quoteRepository.findTopByPlayerIdAndCalculatedAtLessThanEqualOrderByCalculatedAtDesc(
                playerId, date.atTime(LocalTime.MAX)))
                .thenReturn(Optional.of(quote));

        // When
        Optional<Quote> resultado = quoteService.getQuoteAt(playerId, date);

        // Then
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getValue().amount()).isEqualByComparingTo("2.0");
        verify(quoteRepository).findTopByPlayerIdAndCalculatedAtLessThanEqualOrderByCalculatedAtDesc(
                playerId, date.atTime(LocalTime.MAX));
    }

    @Test
    void deberiaRetornarEmptyCuandoNoHayCotizacionAntesDeLaFecha() {
        // Given
        Long playerId = 1L;
        LocalDate date = LocalDate.of(2020, Month.JANUARY, 1);
        when(quoteRepository.findTopByPlayerIdAndCalculatedAtLessThanEqualOrderByCalculatedAtDesc(
                playerId, date.atTime(LocalTime.MAX)))
                .thenReturn(Optional.empty());

        // When
        Optional<Quote> resultado = quoteService.getQuoteAt(playerId, date);

        // Then
        assertThat(resultado).isEmpty();
    }

    private Quote crearQuote(Long playerId, Money value) {
        Quote quote = new Quote();
        quote.setPlayerId(playerId);
        quote.setValue(value);
        quote.setCalculatedAt(LocalDateTime.of(2026, Month.JANUARY, 15, 12, 0));
        quote.setStrategyName("MatchMetrics");
        quote.setStrategyVersion("v1.0");
        return quote;
    }
}

