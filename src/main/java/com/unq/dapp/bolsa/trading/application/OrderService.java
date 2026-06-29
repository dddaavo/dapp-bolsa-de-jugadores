package com.unq.dapp.bolsa.trading.application;

import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.shared.error.DomainException;
import com.unq.dapp.bolsa.trading.api.BuyRequest;
import com.unq.dapp.bolsa.trading.api.OrderResponse;
import com.unq.dapp.bolsa.trading.api.SellRequest;
import com.unq.dapp.bolsa.trading.api.TransactionResponse;
import com.unq.dapp.bolsa.trading.domain.Order;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import com.unq.dapp.bolsa.trading.infrastructure.OrderRepository;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final QuoteService quoteService;
    private final PlayerTokenInventoryRepository inventoryRepository;
    private final TokenHoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final PlayerRepository playerRepository;

    public OrderService(QuoteService quoteService,
                        PlayerTokenInventoryRepository inventoryRepository,
                        TokenHoldingRepository holdingRepository,
                        OrderRepository orderRepository,
                        PlayerRepository playerRepository) {
        this.quoteService = quoteService;
        this.inventoryRepository = inventoryRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(Long userId, OrderType type,
            LocalDate from, LocalDate to, Pageable pageable) {
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Page<Order> orders = orderRepository.findByUserIdWithFilters(userId, type, fromInstant, toInstant, pageable);

        Set<Long> playerIds = orders.stream().map(Order::getPlayerId).collect(Collectors.toSet());
        Map<Long, String> playerNames = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));

        return orders.map(o -> TransactionResponse.from(o, playerNames.getOrDefault(o.getPlayerId(), "Desconocido")));
    }

    @Transactional
    public OrderResponse buy(Long userId, BuyRequest request, String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(OrderResponse::from)
                .orElseGet(() -> executeBuy(userId, request, idempotencyKey));
    }

    @Transactional
    public OrderResponse sell(Long userId, SellRequest request, String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(OrderResponse::from)
                .orElseGet(() -> executeSell(userId, request, idempotencyKey));
    }

    private OrderResponse executeBuy(Long userId, BuyRequest request, String idempotencyKey) {
        BigDecimal unitPrice = resolveCurrentPrice(request.playerId());

        PlayerTokenInventory inventory = inventoryRepository.findById(request.playerId())
                .orElseThrow(() -> new DomainException("NO_INVENTORY",
                        "El jugador no tiene inventario de tokens disponible"));
        inventory.reserve(request.quantity());
        inventoryRepository.save(inventory);

        TokenHolding holding = holdingRepository
                .findByUserIdAndPlayerId(userId, request.playerId())
                .map(h -> { h.addPurchase(unitPrice, request.quantity()); return h; })
                .orElseGet(() -> TokenHolding.createNew(userId, request.playerId(), request.quantity(), unitPrice));
        holdingRepository.save(holding);

        Order order = Order.createBuy(userId, request.playerId(), request.quantity(), unitPrice, idempotencyKey);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }

    private OrderResponse executeSell(Long userId, SellRequest request, String idempotencyKey) {
        BigDecimal unitPrice = resolveCurrentPrice(request.playerId());

        TokenHolding holding = holdingRepository
                .findByUserIdAndPlayerId(userId, request.playerId())
                .orElseThrow(() -> new DomainException("NO_HOLDING",
                        "El usuario no tiene tokens de este jugador"));
        holding.sell(request.quantity());
        if (holding.isSoldOut()) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }

        PlayerTokenInventory inventory = inventoryRepository.findById(request.playerId())
                .orElseThrow(() -> new DomainException("NO_INVENTORY",
                        "Inventario del jugador no encontrado"));
        inventory.release(request.quantity());
        inventoryRepository.save(inventory);

        Order order = Order.createSell(userId, request.playerId(), request.quantity(), unitPrice, idempotencyKey);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }

    private BigDecimal resolveCurrentPrice(Long playerId) {
        return quoteService.getCurrentQuote(playerId)
                .map(q -> q.getValue().amount())
                .orElseThrow(() -> new DomainException("NO_QUOTE",
                        "El jugador no tiene cotización vigente"));
    }
}
