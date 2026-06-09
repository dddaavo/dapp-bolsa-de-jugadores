package com.unq.dapp.bolsa.trading.application;

import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.shared.error.DomainException;
import com.unq.dapp.bolsa.trading.api.BuyRequest;
import com.unq.dapp.bolsa.trading.api.OrderResponse;
import com.unq.dapp.bolsa.trading.api.SellRequest;
import com.unq.dapp.bolsa.trading.domain.Order;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import com.unq.dapp.bolsa.trading.infrastructure.OrderRepository;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OrderService {

    private final QuoteService quoteService;
    private final PlayerTokenInventoryRepository inventoryRepository;
    private final TokenHoldingRepository holdingRepository;
    private final OrderRepository orderRepository;

    public OrderService(QuoteService quoteService,
                        PlayerTokenInventoryRepository inventoryRepository,
                        TokenHoldingRepository holdingRepository,
                        OrderRepository orderRepository) {
        this.quoteService = quoteService;
        this.inventoryRepository = inventoryRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
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

        if (inventory.getHeldBySystem() < request.quantity()) {
            throw new DomainException("INSUFFICIENT_STOCK",
                    "Stock insuficiente: disponible " + inventory.getHeldBySystem()
                    + ", solicitado " + request.quantity());
        }

        inventory.setHeldBySystem(inventory.getHeldBySystem() - request.quantity());
        inventoryRepository.save(inventory);

        TokenHolding holding = holdingRepository
                .findByUserIdAndPlayerId(userId, request.playerId())
                .orElse(null);

        if (holding == null) {
            holding = new TokenHolding();
            holding.setUserId(userId);
            holding.setPlayerId(request.playerId());
            holding.setQuantity(request.quantity());
            holding.setAvgBuyPrice(unitPrice);
        } else {
            BigDecimal totalCost = holding.getAvgBuyPrice()
                    .multiply(BigDecimal.valueOf(holding.getQuantity()))
                    .add(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
            int newQty = holding.getQuantity() + request.quantity();
            holding.setAvgBuyPrice(totalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP));
            holding.setQuantity(newQty);
        }
        holdingRepository.save(holding);

        Order order = buildOrder(userId, request.playerId(), OrderType.BUY,
                request.quantity(), unitPrice, idempotencyKey);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }

    private OrderResponse executeSell(Long userId, SellRequest request, String idempotencyKey) {
        BigDecimal unitPrice = resolveCurrentPrice(request.playerId());

        TokenHolding holding = holdingRepository
                .findByUserIdAndPlayerId(userId, request.playerId())
                .orElseThrow(() -> new DomainException("NO_HOLDING",
                        "El usuario no tiene tokens de este jugador"));

        if (holding.getQuantity() < request.quantity()) {
            throw new DomainException("INSUFFICIENT_HOLDING",
                    "Holding insuficiente: disponible " + holding.getQuantity()
                    + ", solicitado " + request.quantity());
        }

        int remaining = holding.getQuantity() - request.quantity();
        if (remaining == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(remaining);
            holdingRepository.save(holding);
        }

        PlayerTokenInventory inventory = inventoryRepository.findById(request.playerId())
                .orElseThrow(() -> new DomainException("NO_INVENTORY",
                        "Inventario del jugador no encontrado"));
        inventory.setHeldBySystem(inventory.getHeldBySystem() + request.quantity());
        inventoryRepository.save(inventory);

        Order order = buildOrder(userId, request.playerId(), OrderType.SELL,
                request.quantity(), unitPrice, idempotencyKey);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }

    private BigDecimal resolveCurrentPrice(Long playerId) {
        return quoteService.getCurrentQuote(playerId)
                .map(q -> q.getValue().amount())
                .orElseThrow(() -> new DomainException("NO_QUOTE",
                        "El jugador no tiene cotización vigente"));
    }

    private Order buildOrder(Long userId, Long playerId, OrderType type,
                             int quantity, BigDecimal unitPrice, String idempotencyKey) {
        Order order = new Order();
        order.setUserId(userId);
        order.setPlayerId(playerId);
        order.setType(type);
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice);
        order.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        order.setIdempotencyKey(idempotencyKey);
        return order;
    }
}
