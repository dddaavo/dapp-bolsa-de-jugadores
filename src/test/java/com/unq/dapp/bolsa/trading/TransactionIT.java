package com.unq.dapp.bolsa.trading;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.auth.domain.Role;
import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import com.unq.dapp.bolsa.trading.api.BuyRequest;
import com.unq.dapp.bolsa.trading.api.SellRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransactionIT {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private PlayerTokenInventoryRepository inventoryRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private UserRepository userRepository;

    private String userToken;
    private String otherToken;
    private String adminToken;
    private Long userId;
    private Long otherId;
    private Long playerId;

    @BeforeAll
    void setup() {
        Player player = new Player();
        player.setName("Transaction IT Player");
        player.setPosition(Position.MF);
        player.setTeam("Test FC");
        player.setLeague(League.LA_LIGA);
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
        quote.setValue(new com.unq.dapp.bolsa.pricing.domain.Money(BigDecimal.valueOf(2.00), "CREDITS"));
        quote.setCalculatedAt(LocalDateTime.now(ZoneOffset.UTC));
        quote.setStrategyName("MatchMetrics");
        quote.setStrategyVersion("v1.0");
        quoteRepository.save(quote);

        var body1 = Map.of("email", "txn-user@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", body1, AuthResponse.class);
        userToken = restTemplate.postForEntity(baseUrl() + "/auth/login", body1, AuthResponse.class)
                .getBody().accessToken();
        userId = userRepository.findByEmail("txn-user@test.com").orElseThrow().getId();

        var body2 = Map.of("email", "txn-other@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", body2, AuthResponse.class);
        otherToken = restTemplate.postForEntity(baseUrl() + "/auth/login", body2, AuthResponse.class)
                .getBody().accessToken();
        otherId = userRepository.findByEmail("txn-other@test.com").orElseThrow().getId();

        var adminBody = Map.of("email", "txn-admin@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", adminBody, AuthResponse.class);
        var adminUser = userRepository.findByEmail("txn-admin@test.com").orElseThrow();
        adminUser.setRole(Role.ADMIN);
        userRepository.save(adminUser);
        adminToken = restTemplate.postForEntity(baseUrl() + "/auth/login", adminBody, AuthResponse.class)
                .getBody().accessToken();

        buy(playerId, 3, userToken);
        sell(playerId, 1, userToken);
    }

    @Test
    void deberiaRetornarHistorialDelPropioUsuario() {
        // when
        var response = getTransactions(userId, null, userToken);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(2);
    }

    @Test
    void deberiaRetornar403SiConsultaOtroUsuario() {
        // when
        var response = getTransactions(userId, null, otherToken);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deberiaRetornarListaVaciaParaUsuarioSinTransacciones() {
        // when
        var response = getTransactions(otherId, null, otherToken);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isEmpty();
    }

    @Test
    void deberiaFiltrarPorTipo() {
        // when
        var response = getTransactions(userId, "BUY", userToken);

        // then
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void adminPuedeVerTransaccionesDeOtroUsuario() {
        // when
        var response = getTransactions(userId, null, adminToken);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(2);
    }

    @Test
    void deberiaFiltrarPorFechaDesde() {
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        // when
        var response = getTransactionsWithDates(userId, null, today, null, userToken);

        // then
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(2);
    }

    @Test
    void deberiaRetornarVacioSiFromEsMañana() {
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

        // when
        var response = getTransactionsWithDates(userId, null, tomorrow, null, userToken);

        // then
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getTransactions(Long id, String type, String token) {
        return getTransactionsWithDates(id, type, null, null, token);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getTransactionsWithDates(Long id, String type, String from, String to, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        StringBuilder url = new StringBuilder(baseUrl() + "/api/v1/users/" + id + "/transactions?");
        if (type != null) url.append("type=").append(type).append("&");
        if (from != null) url.append("from=").append(from).append("&");
        if (to != null) url.append("to=").append(to).append("&");
        return restTemplate.exchange(url.toString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private void buy(Long pid, int qty, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        restTemplate.exchange(baseUrl() + "/api/v1/orders/buy",
                HttpMethod.POST, new HttpEntity<>(new BuyRequest(pid, qty), headers), String.class);
    }

    private void sell(Long pid, int qty, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        restTemplate.exchange(baseUrl() + "/api/v1/orders/sell",
                HttpMethod.POST, new HttpEntity<>(new SellRequest(pid, qty), headers), String.class);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
