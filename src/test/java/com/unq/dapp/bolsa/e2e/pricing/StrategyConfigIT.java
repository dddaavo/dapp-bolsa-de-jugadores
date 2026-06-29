package com.unq.dapp.bolsa.e2e.pricing;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
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
@ActiveProfiles("e2e")
class StrategyConfigIT {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;

    private String adminToken;
    private String userToken;

    @BeforeAll
    void obtenerTokens() {
        Map<String, String> adminCreds = Map.of("email", "system@bolsa.local", "password", "admin1234");
        ResponseEntity<AuthResponse> adminLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/login", adminCreds, AuthResponse.class);
        adminToken = adminLogin.getBody().accessToken();

        Map<String, String> userBody = Map.of("email", "strategy-user@test.com", "password", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", userBody, AuthResponse.class);
        ResponseEntity<AuthResponse> userLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/login", userBody, AuthResponse.class);
        userToken = userLogin.getBody().accessToken();
    }

    @Test
    void deberiaRetornarListaDeEstrategias() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/strategies",
                HttpMethod.GET, requestConToken(userToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("MatchMetrics");
        assertThat(response.getBody()).contains("PositionWeighted");
        assertThat(response.getBody()).contains("weightsJson");
    }

    @Test
    void deberiaActualizarPesosConRolAdmin() {
        String nuevoJson = "{\"goals\":0.5,\"assists\":0.3,\"rating\":0.2}";
        Map<String, String> body = Map.of("weightsJson", nuevoJson);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headersConToken(adminToken));
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/strategies/MatchMetrics/config",
                HttpMethod.PUT, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"configVersion\":1");
        assertThat(response.getBody()).contains("0.5");
    }

    @Test
    void deberiaRetornar403SiNoEsAdmin() {
        Map<String, String> body = Map.of("weightsJson", "{\"goals\":0.5,\"assists\":0.3,\"rating\":0.2}");
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headersConToken(userToken));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/strategies/MatchMetrics/config",
                HttpMethod.PUT, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deberiaRetornar401SinAutenticacion() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/strategies",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpEntity<Void> requestConToken(String token) {
        return new HttpEntity<>(headersConToken(token));
    }

    private HttpHeaders headersConToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
