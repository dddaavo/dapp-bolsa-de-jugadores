package com.unq.dapp.bolsa.pricing.infrastructure;

import com.unq.dapp.bolsa.pricing.domain.PlayerMetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerMetricsSnapshotRepository extends JpaRepository<PlayerMetricsSnapshot, Long> {

    /**
     * Encuentra la métrica más reciente de un jugador.
     */
    Optional<PlayerMetricsSnapshot> findTopByPlayerIdOrderByPeriodEndDesc(Long playerId);
}

