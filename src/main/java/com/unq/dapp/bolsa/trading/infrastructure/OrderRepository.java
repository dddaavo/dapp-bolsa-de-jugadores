package com.unq.dapp.bolsa.trading.infrastructure;

import com.unq.dapp.bolsa.trading.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
