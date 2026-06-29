package com.unq.dapp.bolsa.trading.infrastructure;

import com.unq.dapp.bolsa.trading.domain.Order;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
           "AND (:type IS NULL OR o.type = :type) " +
           "AND (:from IS NULL OR o.createdAt >= :from) " +
           "AND (:to IS NULL OR o.createdAt < :to) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("type") OrderType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
