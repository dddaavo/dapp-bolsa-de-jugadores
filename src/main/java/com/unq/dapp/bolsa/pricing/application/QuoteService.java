package com.unq.dapp.bolsa.pricing.application;

import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service para consultar cotizaciones de jugadores.
 */
@Service
@Transactional(readOnly = true)
public class QuoteService {

    private final QuoteRepository quoteRepository;

    public QuoteService(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    /**
     * Obtiene la cotización vigente (más reciente) de un jugador.
     *
     * @param playerId ID del jugador
     * @return Cotización actual o empty si no existe
     */
    public Optional<Quote> getCurrentQuote(Long playerId) {
        return quoteRepository.findTopByPlayerIdOrderByCalculatedAtDesc(playerId);
    }

    /**
     * Obtiene el historial de cotizaciones de un jugador en un rango de fechas.
     *
     * @param playerId ID del jugador
     * @param from Fecha inicio (inclusive)
     * @param to Fecha fin (inclusive)
     * @return Lista de cotizaciones ordenadas por fecha descendente
     */
    public List<Quote> getQuoteHistory(Long playerId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);
        return quoteRepository.findByPlayerIdAndCalculatedAtBetweenOrderByCalculatedAtDesc(
            playerId, fromDateTime, toDateTime
        );
    }

    /**
     * Obtiene todas las cotizaciones de un jugador.
     *
     * @param playerId ID del jugador
     * @return Lista de todas las cotizaciones ordenadas por fecha descendente
     */
    public List<Quote> getAllQuotes(Long playerId) {
        return quoteRepository.findByPlayerIdOrderByCalculatedAtDesc(playerId);
    }
}

