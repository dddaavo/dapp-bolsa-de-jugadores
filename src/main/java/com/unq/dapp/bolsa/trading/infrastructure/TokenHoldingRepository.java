package com.unq.dapp.bolsa.trading.infrastructure;

import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TokenHoldingRepository extends JpaRepository<TokenHolding, Long> {

    Optional<TokenHolding> findByUserIdAndPlayerId(Long userId, Long playerId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT t FROM TokenHolding t WHERE t.userId = :userId AND t.playerId = :playerId")
    Optional<TokenHolding> findByUserIdAndPlayerIdForUpdate(Long userId, Long playerId);
}
