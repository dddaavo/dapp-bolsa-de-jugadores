package com.unq.dapp.bolsa.pricing.infrastructure;

import com.unq.dapp.bolsa.pricing.domain.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    Optional<StrategyConfig> findByName(String name);
    List<StrategyConfig> findAllByActiveTrue();
}
