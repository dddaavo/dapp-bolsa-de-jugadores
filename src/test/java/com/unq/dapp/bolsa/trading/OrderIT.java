package com.unq.dapp.bolsa.trading;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import com.unq.dapp.bolsa.trading.api.BuyRequest;
import com.unq.dapp.bolsa.trading.api.OrderResponse;
import com.unq.dapp.bolsa.trading.api.SellRequest;
import com.unq.dapp.bolsa.trading.infrastructure.OrderRepository;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderIT {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private PlayerTokenInventoryRepository inventoryRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TokenHoldingRepository holdingRepository;

    private String userToken;
    private Long playerId;

    @BeforeAll
    void setup() {
        Player player = new Player();
        player.setName("IT Player Trading");
        player.setPosition(Position.FW);
        player.setTeam("Test FC");
        player.setLeague(League.PREMIER_LEAGUE);
        player.setActive(true);
        playerId = playerRepository.save(player).getId();

        PlayerTokenInventory inventory = new PlayerTokenInventory();
        inventory.setPlayerId(playerId);
        inventory.setTotalEmitted(100);
        inventory.setHeldBySystem(100);
        inventory.setInitialTokenValue(BigDecimal.ONE);
        inventoryRepository.save(inventory);

        Quote quote = new Quote();
        quote.setPlayerId(playerId);
        quote.setValue(new com.unq.dapp.bolsa.pricing.domain.Money(BigDecimal.valueOf(1.85), "CREDITS"));
        quote.setCalculatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        quote.setStrategyName("MatchMetrics");
        quote.setStrategyVersion("v1.0");
        quoteRepository.save(quote);

        var body = Map.of("email", "order-it@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", body, AuthResponse.class);
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity(
                baseUrl() + "/auth/login", body, AuthResponse.class);
        userToken = login.getBody().accessToken();
    }

    @Test
    void deberiaComprarTokensExitosamente() {
        long ordersBefore = orderRepository.count();

        // when
        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/buy",
                HttpMethod.POST,
                buyRequest(playerId, 5, UUID.randomUUID().toString()),
                OrderResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().orderId()).isNotNull();
        assertThat(response.getBody().quantity()).isEqualTo(5);
        assertThat(response.getBody().unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(1.85));
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertThat(response.getBody().totalAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(1.85).multiply(BigDecimal.valueOf(5)));
        assertThat(holdingRepository.findAll().stream()
                .anyMatch(h -> h.getPlayerId().equals(playerId))).isTrue();
    }

    @Test
    void deberiaVenderTokensExitosamente() {
        restTemplate.exchange(baseUrl() + "/api/v1/orders/buy",
                HttpMethod.POST, buyRequest(playerId, 3, UUID.randomUUID().toString()), OrderResponse.class);

        // when
        ResponseEntity<OrderResponse> sellResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/sell",
                HttpMethod.POST,
                sellRequest(playerId, 2, UUID.randomUUID().toString()),
                OrderResponse.class);

        // then
        assertThat(sellResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sellResponse.getBody().quantity()).isEqualTo(2);
    }

    @Test
    void deberiaFallarSiNoHayStockSuficiente() {
        // when
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/buy",
                HttpMethod.POST,
                buyRequest(playerId, 999, UUID.randomUUID().toString()),
                String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("INSUFFICIENT_STOCK");
    }

    @Test
    void deberiaFallarSiNohayHoldingSuficienteParaVender() {
        // when
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/sell",
                HttpMethod.POST,
                sellRequest(999L, 1, UUID.randomUUID().toString()),
                String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void deberiaFallarSiFaltaIdempotencyKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BuyRequest> request = new HttpEntity<>(new BuyRequest(playerId, 1), headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/buy", HttpMethod.POST, request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("MISSING_IDEMPOTENCY_KEY");
    }

    @Test
    void deberiaRetornarMismaOrdenConIdempotencyKeyRepetida() {
        String key = UUID.randomUUID().toString();
        ResponseEntity<OrderResponse> first = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/buy",
                HttpMethod.POST, buyRequest(playerId, 1, key), OrderResponse.class);

        // when
        ResponseEntity<OrderResponse> second = restTemplate.exchange(
                baseUrl() + "/api/v1/orders/buy",
                HttpMethod.POST, buyRequest(playerId, 1, key), OrderResponse.class);

        // then
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().orderId()).isEqualTo(first.getBody().orderId());
    }

    private HttpEntity<BuyRequest> buyRequest(Long pid, int qty, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return new HttpEntity<>(new BuyRequest(pid, qty), headers);
    }

    private HttpEntity<SellRequest> sellRequest(Long pid, int qty, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return new HttpEntity<>(new SellRequest(pid, qty), headers);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
