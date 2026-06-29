package com.unq.dapp.bolsa.trading.application;

import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.domain.Quote;
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
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private QuoteService quoteService;
    @Mock private PlayerTokenInventoryRepository inventoryRepository;
    @Mock private TokenHoldingRepository holdingRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PlayerRepository playerRepository;

    private OrderService orderService;

    private static final Long USER_ID = 1L;
    private static final Long PLAYER_ID = 10L;
    private static final BigDecimal UNIT_PRICE = BigDecimal.valueOf(5.00);
    private static final String IDEMPOTENCY_KEY = "test-key-001";

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                quoteService, inventoryRepository, holdingRepository, orderRepository, playerRepository);
    }

    // --- BUY ---

    @Test
    void deberiaBuyTokensCuandoHayStockYSinHoldingPrevio() {
        stubQuote(UNIT_PRICE);
        stubInventory(50);
        when(holdingRepository.findByUserIdAndPlayerId(USER_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        OrderResponse response = orderService.buy(USER_ID, new BuyRequest(PLAYER_ID, 10), IDEMPOTENCY_KEY);

        assertThat(response.playerId()).isEqualTo(PLAYER_ID);
        assertThat(response.type()).isEqualTo(OrderType.BUY);
        assertThat(response.quantity()).isEqualTo(10);
        assertThat(response.unitPrice()).isEqualByComparingTo(UNIT_PRICE);
        verify(inventoryRepository).save(any());
        verify(holdingRepository).save(any());
        verify(orderRepository).save(any());
    }

    @Test
    void deberiaActualizarAvgBuyPriceCuandoYaTieneHolding() {
        stubQuote(BigDecimal.valueOf(10.00));
        stubInventory(50);

        TokenHolding existing = new TokenHolding();
        existing.setUserId(USER_ID);
        existing.setPlayerId(PLAYER_ID);
        existing.setQuantity(5);
        existing.setAvgBuyPrice(BigDecimal.valueOf(8.00));
        when(holdingRepository.findByUserIdAndPlayerId(USER_ID, PLAYER_ID)).thenReturn(Optional.of(existing));
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        orderService.buy(USER_ID, new BuyRequest(PLAYER_ID, 5), IDEMPOTENCY_KEY);

        // avgPrice = (5*8 + 5*10) / 10 = 90/10 = 9.00
        assertThat(existing.getAvgBuyPrice()).isEqualByComparingTo("9.0000");
        assertThat(existing.getQuantity()).isEqualTo(10);
    }

    @Test
    void deberiaRetornarOrdenExistenteCuandoIdempotencyKeyYaUsada() {
        Order existingOrder = buildOrder(OrderType.BUY, 3);
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existingOrder));

        orderService.buy(USER_ID, new BuyRequest(PLAYER_ID, 3), IDEMPOTENCY_KEY);

        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deberiaLanzarExcepcionCuandoStockInsuficienteEnBuy() {
        stubQuote(UNIT_PRICE);
        stubInventory(5);
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.buy(USER_ID, new BuyRequest(PLAYER_ID, 10), IDEMPOTENCY_KEY))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");
    }

    @Test
    void deberiaLanzarExcepcionCuandoJugadorSinCotizacionEnBuy() {
        when(quoteService.getCurrentQuote(PLAYER_ID)).thenReturn(Optional.empty());
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.buy(USER_ID, new BuyRequest(PLAYER_ID, 1), IDEMPOTENCY_KEY))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NO_QUOTE");
    }

    // --- SELL ---

    @Test
    void deberiaSellTokensCuandoTieneHoldingSuficiente() {
        stubQuote(UNIT_PRICE);
        stubInventory(80);

        TokenHolding holding = buildHolding(10);
        when(holdingRepository.findByUserIdAndPlayerId(USER_ID, PLAYER_ID)).thenReturn(Optional.of(holding));
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        OrderResponse response = orderService.sell(USER_ID, new SellRequest(PLAYER_ID, 4), IDEMPOTENCY_KEY);

        assertThat(response.type()).isEqualTo(OrderType.SELL);
        assertThat(response.quantity()).isEqualTo(4);
        assertThat(holding.getQuantity()).isEqualTo(6);
        verify(holdingRepository).save(holding);
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void deberiaBorrarHoldingCuandoVendeTodosLosTokens() {
        stubQuote(UNIT_PRICE);
        stubInventory(80);

        TokenHolding holding = buildHolding(5);
        when(holdingRepository.findByUserIdAndPlayerId(USER_ID, PLAYER_ID)).thenReturn(Optional.of(holding));
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        orderService.sell(USER_ID, new SellRequest(PLAYER_ID, 5), IDEMPOTENCY_KEY);

        verify(holdingRepository).delete(holding);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void deberiaLanzarExcepcionCuandoHoldingInsuficienteEnSell() {
        stubQuote(UNIT_PRICE);

        TokenHolding holding = buildHolding(2);
        when(holdingRepository.findByUserIdAndPlayerId(USER_ID, PLAYER_ID)).thenReturn(Optional.of(holding));
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.sell(USER_ID, new SellRequest(PLAYER_ID, 5), IDEMPOTENCY_KEY))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_HOLDING");
    }

    @Test
    void deberiaLanzarExcepcionCuandoNoTieneHoldingAlVender() {
        stubQuote(UNIT_PRICE);
        when(holdingRepository.findByUserIdAndPlayerId(USER_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.sell(USER_ID, new SellRequest(PLAYER_ID, 1), IDEMPOTENCY_KEY))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NO_HOLDING");
    }

    // --- helpers ---

    private void stubQuote(BigDecimal price) {
        Quote quote = new Quote();
        quote.setPlayerId(PLAYER_ID);
        quote.setValue(new Money(price, "CREDITS"));
        when(quoteService.getCurrentQuote(PLAYER_ID)).thenReturn(Optional.of(quote));
    }

    private void stubInventory(int heldBySystem) {
        PlayerTokenInventory inv = new PlayerTokenInventory();
        inv.setPlayerId(PLAYER_ID);
        inv.setTotalEmitted(100);
        inv.setHeldBySystem(heldBySystem);
        when(inventoryRepository.findById(PLAYER_ID)).thenReturn(Optional.of(inv));
    }

    private TokenHolding buildHolding(int quantity) {
        TokenHolding h = new TokenHolding();
        h.setUserId(USER_ID);
        h.setPlayerId(PLAYER_ID);
        h.setQuantity(quantity);
        h.setAvgBuyPrice(UNIT_PRICE);
        return h;
    }

    private Order buildOrder(OrderType type, int quantity) {
        Order o = new Order();
        o.setUserId(USER_ID);
        o.setPlayerId(PLAYER_ID);
        o.setType(type);
        o.setQuantity(quantity);
        o.setUnitPrice(UNIT_PRICE);
        o.setTotalAmount(UNIT_PRICE.multiply(BigDecimal.valueOf(quantity)));
        o.setIdempotencyKey(IDEMPOTENCY_KEY);
        return o;
    }
}
