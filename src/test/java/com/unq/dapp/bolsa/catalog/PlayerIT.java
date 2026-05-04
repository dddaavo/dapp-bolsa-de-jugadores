package com.unq.dapp.bolsa.catalog;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlayerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String token;

    @BeforeEach
    void obtenerToken() {
        var body = Map.of("email", "player-it@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", body, AuthResponse.class);
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity(
                baseUrl() + "/auth/login", body, AuthResponse.class);
        token = login.getBody().accessToken();
    }

    @Test
    void deberiaRetornarJugadoresCargadosPorDataInitializer() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players?page=0&size=25",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Erling Haaland");
    }

    @Test
    void deberiaFiltrarJugadoresPorLiga() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players?league=LA_LIGA",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("LA_LIGA");
        assertThat(response.getBody()).doesNotContain("PREMIER_LEAGUE");
    }

    @Test
    void deberiaRetornar200CuandoJugadorExiste() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/1",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deberiaRetornar404CuandoJugadorNoExiste() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/9999",
                HttpMethod.GET, requestConToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("PLAYER_NOT_FOUND");
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
