package com.unq.dapp.bolsa.e2e;

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
class ActuatorIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;
    private String userToken;

    @BeforeAll
    void setUp() {
        adminToken = obtenerToken("system@bolsa.local", "admin1234");
        userToken = obtenerToken("alice@example.com", "Alice1234");
    }

    @Test
    void healthDeberiaSerPublico() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheusDeberiaResponderComoAdmin() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/actuator/prometheus"), HttpMethod.GET, headersConToken(adminToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# HELP", "# TYPE");
    }

    @Test
    void prometheusDeberiaSerPublicoParaCualquierUsuario() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/actuator/prometheus"), HttpMethod.GET, headersConToken(userToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheusDeberiaSerPublicoSinToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/prometheus"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# HELP", "# TYPE");
    }

    @Test
    void prometheusDeberiaExponerMetricasDeOrdenes() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/actuator/prometheus"), HttpMethod.GET, headersConToken(adminToken), String.class);
        assertThat(response.getBody()).contains("orders_total");
    }

    @Test
    void prometheusDeberiaExponerMetricasDeRecalculacion() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/actuator/prometheus"), HttpMethod.GET, headersConToken(adminToken), String.class);
        assertThat(response.getBody()).contains("quotes_recalculation_duration");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Void> headersConToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String obtenerToken(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            url("/auth/login"),
            Map.of("email", email, "password", password),
            AuthResponse.class);
        return response.getBody().accessToken();
    }
}
