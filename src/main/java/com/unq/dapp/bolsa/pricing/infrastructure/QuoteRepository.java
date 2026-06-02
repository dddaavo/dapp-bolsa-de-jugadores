package com.unq.dapp.bolsa.pricing.infrastructure;

import com.unq.dapp.bolsa.pricing.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    /**
     * Encuentra la cotización más reciente de un jugador.
     */
    Optional<Quote> findTopByPlayerIdOrderByCalculatedAtDesc(Long playerId);

    /**
     * Encuentra todas las cotizaciones de un jugador ordenadas por fecha descendente.
     */
    List<Quote> findByPlayerIdOrderByCalculatedAtDesc(Long playerId);

    /**
     * Encuentra las cotizaciones de un jugador en un rango de fechas.
     */
    List<Quote> findByPlayerIdAndCalculatedAtBetweenOrderByCalculatedAtDesc(
            Long playerId,
            LocalDateTime from,
            LocalDateTime to
    );
}

