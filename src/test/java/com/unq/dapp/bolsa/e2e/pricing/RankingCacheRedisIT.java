package com.unq.dapp.bolsa.e2e.pricing;

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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de caché contra un Redis REAL (Testcontainers).
 * A diferencia de {@link RankingCacheIT} —que usa un CacheManager in-memory—,
 * este IT ejercita el {@link RedisCacheManager} real: verifica el round-trip de
 * serialización (JSON + typing polimórfico) de {@code List<Quote>} con
 * {@code LocalDateTime}, y la invalidación distribuida al recalcular.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:e2e-rediscachedb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
)
@ActiveProfiles("e2e")
class RankingCacheRedisIT {

    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CacheManager cacheManager;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private QuoteRepository quoteRepository;

    private String userToken;
    private String adminToken;

    @BeforeAll
    void setUp() {
        adminToken = login("system@bolsa.local", "admin1234");
        restTemplate.postForEntity(
                baseUrl() + "/auth/register",
                Map.of("email", "redis-cache-it@test.com", "password", "Password123"),
                AuthResponse.class);
        userToken = login("redis-cache-it@test.com", "Password123");
        seedPlayerWithQuote();
    }

    @BeforeEach
    void limpiarCache() {
        Objects.requireNonNull(cacheManager.getCache("ranking")).clear();
    }

    @Test
    void elCacheManagerDeberiaSerRedisReal() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    void deberiaSerializarYLeerElRankingDesdeRedis() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/players/ranking", HttpMethod.GET, conToken(userToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Cache.ValueWrapper cached = Objects.requireNonNull(cacheManager.getCache("ranking")).get("default:10");
        assertThat(cached).isNotNull();

        Object value = cached.get();
        assertThat(value).isInstanceOf(List.class);
        List<?> quotes = (List<?>) value;
        assertThat(quotes).isNotEmpty();
        assertThat(quotes.get(0)).isInstanceOf(Quote.class);
        assertThat(((Quote) quotes.get(0)).getCalculatedAt()).isNotNull();
    }

    @Test
    void deberiaInvalidarElRankingEnRedisTrasRecalcular() {
        restTemplate.exchange(baseUrl() + "/api/v1/players/ranking",
                HttpMethod.GET, conToken(userToken), String.class);
        assertThat(Objects.requireNonNull(cacheManager.getCache("ranking")).get("default:10")).isNotNull();

        restTemplate.exchange(baseUrl() + "/api/v1/quotes/recalculate",
                HttpMethod.POST, conToken(adminToken), String.class);

        assertThat(Objects.requireNonNull(cacheManager.getCache("ranking")).get("default:10")).isNull();
    }

    private void seedPlayerWithQuote() {
        Player player = new Player();
        player.setExternalId("redis-it-1");
        player.setName("Redis IT Player");
        player.setPosition(Position.FW);
        player.setTeam("Test FC");
        player.setLeague(League.PREMIER_LEAGUE);
        player.setNationality("");
        player.setActive(true);
        Player saved = playerRepository.save(player);

        Quote quote = new Quote();
        quote.setPlayerId(saved.getId());
        quote.setValue(new Money(new BigDecimal("1.75"), "CREDITS"));
        quote.setCalculatedAt(LocalDateTime.now());
        quote.setStrategyName("GlobalMetrics");
        quote.setStrategyVersion("v1.0");
        quoteRepository.save(quote);
    }

    private String login(String email, String password) {
        ResponseEntity<AuthResponse> resp = restTemplate.postForEntity(
                baseUrl() + "/auth/login",
                Map.of("email", email, "password", password),
                AuthResponse.class);
        return Objects.requireNonNull(resp.getBody()).accessToken();
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
