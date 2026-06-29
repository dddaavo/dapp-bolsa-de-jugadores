package com.unq.dapp.bolsa.trading.infrastructure;

import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenHoldingRepository extends JpaRepository<TokenHolding, Long> {

    Optional<TokenHolding> findByUserIdAndPlayerId(Long userId, Long playerId);

    List<TokenHolding> findAllByUserId(Long userId);
}
