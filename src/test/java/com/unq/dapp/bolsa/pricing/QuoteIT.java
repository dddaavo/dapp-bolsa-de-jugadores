package com.unq.dapp.bolsa.pricing;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
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
@ActiveProfiles("test")
class QuoteIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    private String token;
    private Long playerId;

    @BeforeAll
    void seedData() {
        Player player = new Player();
        player.setName("Kylian Mbappé");
        player.setPosition(Position.FW);
        player.setTeam("Real Madrid");
        player.setLeague(League.LA_LIGA);
        player.setActive(true);
        playerId = playerRepository.save(player).getId();

        Quote q1 = new Quote();
        q1.setPlayerId(playerId);
        q1.setValue(new Money(BigDecimal.valueOf(10.00), "CREDITS"));
        q1.setCalculatedAt(LocalDateTime.of(2026, 5, 1, 8, 0));
        q1.setStrategyName("matchMetrics");
        q1.setStrategyVersion("1.0");
        quoteRepository.save(q1);

        Quote q2 = new Quote();
        q2.setPlayerId(playerId);
        q2.setValue(new Money(BigDecimal.valueOf(15.50), "CREDITS"));
        q2.setCalculatedAt(LocalDateTime.of(2026, 6, 1, 8, 0));
        q2.setStrategyName("matchMetrics");
        q2.setStrategyVersion("1.0");
        quoteRepository.save(q2);
    }

    @BeforeEach
    void obtenerToken() {
        var body = Map.of("email", "quote-it@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", body, AuthResponse.class);
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity(
                baseUrl() + "/auth/login", body, AuthResponse.class);
        token = login.getBody().accessToken();
    }

    @Test
    void deberiaRetornarCotizacionActual() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/" + playerId + "/quotes/current",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("15.5");
        assertThat(response.getBody()).contains("matchMetrics");
    }

    @Test
    void deberiaRetornar404SiJugadorSinCotizacion() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/9999/quotes/current",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("QUOTE_NOT_FOUND");
    }

    @Test
    void deberiaRetornarHistorialCompleto() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/" + playerId + "/quotes",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("10.0");
        assertThat(response.getBody()).contains("15.5");
    }

    @Test
    void deberiaFiltrarHistorialPorFecha() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/" + playerId + "/quotes?from=2026-06-01&to=2026-06-30",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("15.5");
        assertThat(response.getBody()).doesNotContain("10.0");
    }

    @Test
    void deberiaRetornarRanking() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/ranking",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Kylian Mbappé");
        assertThat(response.getBody()).contains("rankPosition");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpEntity<Void> requestConToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
