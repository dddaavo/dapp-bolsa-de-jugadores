package com.unq.dapp.bolsa.pricing;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class QuoteRecalculationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    private String adminToken;
    private String userToken;

    @BeforeAll
    void setup() {
        Player player = new Player();
        player.setName("Lamine Yamal");
        player.setPosition(Position.FW);
        player.setTeam("FC Barcelona");
        player.setLeague(League.LA_LIGA);
        player.setActive(true);
        playerRepository.save(player);

        var loginBody = Map.of("email", "system@bolsa.local", "password", "admin1234");
        ResponseEntity<AuthResponse> adminLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/login", loginBody, AuthResponse.class);
        adminToken = adminLogin.getBody().accessToken();

        var userBody = Map.of("email", "recalc-it@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", userBody, AuthResponse.class);
        ResponseEntity<AuthResponse> userLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/login", userBody, AuthResponse.class);
        userToken = userLogin.getBody().accessToken();
    }

    @Test
    void deberiaRecalcularConAdminYRetornar200() {
        long quotesBefore = quoteRepository.count();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/quotes/recalculate",
                HttpMethod.POST, requestConToken(adminToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalRecalculated");
        assertThat(quoteRepository.count()).isGreaterThan(quotesBefore);
    }

    @Test
    void deberiaRetornar403ConRolUser() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/quotes/recalculate",
                HttpMethod.POST, requestConToken(userToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deberiaRetornar401SinToken() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/quotes/recalculate",
                HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deberiaAceptarEstrategiaEspecificaEnCuerpo() {
        var body = Map.of("strategyName", "matchMetrics");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/quotes/recalculate",
                HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("matchMetrics");
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
