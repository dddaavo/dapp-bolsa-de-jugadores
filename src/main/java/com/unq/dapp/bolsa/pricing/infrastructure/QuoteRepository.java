package com.unq.dapp.bolsa.pricing.infrastructure;

import com.unq.dapp.bolsa.pricing.domain.Quote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    Optional<Quote> findTopByPlayerIdOrderByCalculatedAtDesc(Long playerId);

    List<Quote> findByPlayerIdOrderByCalculatedAtDesc(Long playerId);

    List<Quote> findByPlayerIdAndCalculatedAtBetweenOrderByCalculatedAtDesc(
            Long playerId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<Quote> findTopByPlayerIdAndCalculatedAtLessThanEqualOrderByCalculatedAtDesc(
            Long playerId, LocalDateTime dateTime);

    @Query("SELECT q FROM Quote q WHERE q.calculatedAt = " +
           "(SELECT MAX(q2.calculatedAt) FROM Quote q2 WHERE q2.playerId = q.playerId) " +
           "ORDER BY q.valueAmount DESC")
    List<Quote> findLatestPerPlayerOrderByValueDesc(Pageable pageable);

    @Query("SELECT q FROM Quote q WHERE q.strategyName = :strategyName " +
           "AND q.calculatedAt = (SELECT MAX(q2.calculatedAt) FROM Quote q2 " +
           "WHERE q2.playerId = q.playerId AND q2.strategyName = :strategyName) " +
           "ORDER BY q.valueAmount DESC")
    List<Quote> findLatestPerPlayerByStrategyOrderByValueDesc(@Param("strategyName") String strategyName, Pageable pageable);
}

