package com.unq.dapp.bolsa.e2e.auth;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
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
@ActiveProfiles("e2e")
class AuthIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void registroDevuelve201ConToken() {
        var body = Map.of("email", "nuevo@test.com", "password", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", body, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void loginDevuelve200ConToken() {
        var registerBody = Map.of("email", "login@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", registerBody, AuthResponse.class);

        var loginBody = Map.of("email", "login@test.com", "password", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/login", loginBody, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void requestSinTokenAEndpointProtegidoDevuelve401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/players", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestConTokenMalformadoDevuelve401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("esto.no.es.un.jwt.valido");
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestConTokenValidoNoDevuelve401() {
        var body = Map.of("email", "auth@test.com", "password", "password123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/register", body, AuthResponse.class);
        String token = registerResponse.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
