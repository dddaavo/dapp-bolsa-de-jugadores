package com.unq.dapp.bolsa.e2e.pricing;

import com.unq.dapp.bolsa.auth.api.AuthResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:e2e-cachedb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@ActiveProfiles("e2e")
class RankingCacheIT {

    @TestConfiguration
    static class InMemoryCacheOverride {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("ranking");
        }
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CacheManager cacheManager;

    private String userToken;
    private String adminToken;

    @BeforeAll
    void setUp() {
        adminToken = login("system@bolsa.local", "admin1234");
        restTemplate.postForEntity(
                baseUrl() + "/auth/register",
                Map.of("email", "ranking-cache-it@test.com", "password", "Password123"),
                AuthResponse.class);
        userToken = login("ranking-cache-it@test.com", "Password123");
    }

    @BeforeEach
    void limpiarCache() {
        Objects.requireNonNull(cacheManager.getCache("ranking")).clear();
    }

    @Test
    void deberiaPopularCacheTrasConsultaDeRanking() {
        restTemplate.exchange(baseUrl() + "/api/v1/players/ranking",
                HttpMethod.GET, conToken(userToken), String.class);

        Cache.ValueWrapper cached = cacheManager.getCache("ranking").get("default:10");
        assertThat(cached).isNotNull();
    }

    @Test
    void deberiaEvictarCacheTrasRecalcularCotizaciones() {
        restTemplate.exchange(baseUrl() + "/api/v1/players/ranking",
                HttpMethod.GET, conToken(userToken), String.class);
        assertThat(cacheManager.getCache("ranking").get("default:10")).isNotNull();

        restTemplate.exchange(baseUrl() + "/api/v1/quotes/recalculate",
                HttpMethod.POST, conToken(adminToken), String.class);

        assertThat(cacheManager.getCache("ranking").get("default:10")).isNull();
    }

    private String login(String email, String password) {
        ResponseEntity<AuthResponse> resp = restTemplate.postForEntity(
                baseUrl() + "/auth/login",
                Map.of("email", email, "password", password),
                AuthResponse.class);
        return resp.getBody().accessToken();
    }

    private HttpEntity<Void> conToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(headers);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
