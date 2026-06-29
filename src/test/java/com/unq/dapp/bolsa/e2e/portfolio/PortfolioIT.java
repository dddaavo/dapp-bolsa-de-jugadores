package com.unq.dapp.bolsa.e2e.portfolio;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class PortfolioIT {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private TokenHoldingRepository holdingRepository;

    private String ownerToken;
    private String otherToken;
    private Long ownerId;
    private Long playerId;

    @BeforeAll
    void seedData() {
        Player player = new Player();
        player.setName("Portfolio Player");
        player.setPosition(Position.FW);
        player.setTeam("Test FC");
        player.setLeague(League.PREMIER_LEAGUE);
        player.setActive(true);
        playerId = playerRepository.save(player).getId();

        Quote quote = new Quote();
        quote.setPlayerId(playerId);
        quote.setValue(new Money(BigDecimal.valueOf(12.00), "CREDITS"));
        quote.setCalculatedAt(LocalDateTime.now());
        quote.setStrategyName("matchMetrics");
        quote.setStrategyVersion("1.0");
        quoteRepository.save(quote);
    }

    @BeforeEach
    void obtenerTokens() {
        var ownerBody = Map.of("email", "portfolio-owner@test.com", "password", "password123");
        ResponseEntity<AuthResponse> reg = restTemplate.postForEntity(baseUrl() + "/auth/register", ownerBody, AuthResponse.class);
        if (reg.getStatusCode() == HttpStatus.CREATED) {
            ownerId = reg.getBody().userId();
            TokenHolding holding = new TokenHolding();
            holding.setUserId(ownerId);
            holding.setPlayerId(playerId);
            holding.setQuantity(10);
            holding.setAvgBuyPrice(BigDecimal.valueOf(10.00));
            holdingRepository.save(holding);
        }
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity(baseUrl() + "/auth/login", ownerBody, AuthResponse.class);
        ownerToken = login.getBody().accessToken();
        if (ownerId == null) ownerId = login.getBody().userId();

        var otherBody = Map.of("email", "portfolio-other@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", otherBody, AuthResponse.class);
        ResponseEntity<AuthResponse> otherLogin = restTemplate.postForEntity(baseUrl() + "/auth/login", otherBody, AuthResponse.class);
        otherToken = otherLogin.getBody().accessToken();
    }

    @Test
    void deberiaRetornarPortfolioConPosicionesYCalculos() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/users/" + ownerId + "/portfolio",
                HttpMethod.GET, requestConToken(ownerToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("positions");
        assertThat(response.getBody()).contains("Portfolio Player");
        assertThat(response.getBody()).contains("totalInvested");
        assertThat(response.getBody()).contains("totalCurrentValue");
        assertThat(response.getBody()).contains("totalProfitLoss");
    }

    @Test
    void deberiaRetornarPortfolioVacioCuandoSinHoldings() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/users/" + ownerId + "/portfolio",
                HttpMethod.GET, requestConToken(otherToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deberiaRetornar401SinAutenticacion() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/users/" + ownerId + "/portfolio",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpEntity<Void> requestConToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
