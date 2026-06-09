package com.unq.dapp.bolsa.pricing.infrastructure;

import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class QuoteRepositoryTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 15, 12, 0);

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deberiaGuardarYRecuperarQuote() {
        // Given
        Quote quote = crearQuote(1L, Money.of(1.5), BASE_TIME);

        // When
        Quote saved = quoteRepository.save(quote);
        entityManager.flush();
        entityManager.clear();

        Optional<Quote> found = quoteRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getPlayerId()).isEqualTo(1L);
        assertThat(found.get().getValue().amount()).isEqualByComparingTo(Money.of(1.5).amount());
        assertThat(found.get().getStrategyName()).isEqualTo("MatchMetrics");
    }

    @Test
    void deberiaEncontrarCotizacionMasReciente() {
        // Given
        Long playerId = 1L;
        Quote quote1 = crearQuote(playerId, Money.of(1.5), BASE_TIME.minusDays(2));
        Quote quote2 = crearQuote(playerId, Money.of(1.6), BASE_TIME.minusDays(1));
        Quote quote3 = crearQuote(playerId, Money.of(1.7), BASE_TIME);

        quoteRepository.saveAll(List.of(quote1, quote2, quote3));
        entityManager.flush();

        // When
        Optional<Quote> resultado = quoteRepository.findTopByPlayerIdOrderByCalculatedAtDesc(playerId);

        // Then
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getValue().amount()).isEqualByComparingTo(Money.of(1.7).amount());
    }

    @Test
    void deberiaFiltrarPorRangoDeFechas() {
        // Given
        Long playerId = 1L;
        LocalDateTime now = BASE_TIME;
        Quote quote1 = crearQuote(playerId, Money.of(1.5), now.minusDays(10));
        Quote quote2 = crearQuote(playerId, Money.of(1.6), now.minusDays(5));
        Quote quote3 = crearQuote(playerId, Money.of(1.7), now.minusDays(2));
        Quote quote4 = crearQuote(playerId, Money.of(1.8), now);

        quoteRepository.saveAll(List.of(quote1, quote2, quote3, quote4));
        entityManager.flush();

        // When - buscar entre hace 6 días y hace 1 día
        List<Quote> resultado = quoteRepository.findByPlayerIdAndCalculatedAtBetweenOrderByCalculatedAtDesc(
                playerId,
                now.minusDays(6),
                now.minusDays(1)
        );

        // Then
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getValue().amount()).isEqualByComparingTo(Money.of(1.7).amount());
        assertThat(resultado.get(1).getValue().amount()).isEqualByComparingTo(Money.of(1.6).amount());
    }

    @Test
    void deberiaEncontrarTodasLasCotizacionesDeUnJugador() {
        // Given
        Long playerId = 1L;
        Quote quote1 = crearQuote(playerId, Money.of(1.5), BASE_TIME.minusDays(3));
        Quote quote2 = crearQuote(playerId, Money.of(1.6), BASE_TIME.minusDays(2));
        Quote quote3 = crearQuote(playerId, Money.of(1.7), BASE_TIME.minusDays(1));

        quoteRepository.saveAll(List.of(quote1, quote2, quote3));
        entityManager.flush();

        // When
        List<Quote> resultado = quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(playerId);

        // Then
        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getValue().amount()).isEqualByComparingTo(Money.of(1.7).amount());
    }

    @Test
    void deberiaRetornarEmptySiNoHayCotizaciones() {
        // When
        Optional<Quote> resultado = quoteRepository.findTopByPlayerIdOrderByCalculatedAtDesc(999L);

        // Then
        assertThat(resultado).isEmpty();
    }

    @Test
    void deberiaPersistirAuditoriaCorrectamente() {
        // Given
        Quote quote = crearQuote(1L, Money.of(1.5), BASE_TIME);

        // When
        Quote saved = quoteRepository.save(quote);
        entityManager.flush();

        // Then - verifica campos de auditoría (de AuditableEntity)
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private Quote crearQuote(Long playerId, Money value, LocalDateTime calculatedAt) {
        Quote quote = new Quote();
        quote.setPlayerId(playerId);
        quote.setValue(value);
        quote.setCalculatedAt(calculatedAt);
        quote.setStrategyName("MatchMetrics");
        quote.setStrategyVersion("v1.0");
        return quote;
    }
}

