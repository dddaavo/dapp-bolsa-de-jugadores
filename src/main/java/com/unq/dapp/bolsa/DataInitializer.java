package com.unq.dapp.bolsa;

import com.unq.dapp.bolsa.auth.domain.Role;
import com.unq.dapp.bolsa.auth.domain.User;
import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import com.unq.dapp.bolsa.pricing.application.QuoteRecalculationOrchestrator;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.PlayerMetricsSnapshot;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.domain.StrategyConfig;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.StrategyConfigRepository;
import com.unq.dapp.bolsa.trading.domain.Order;
import com.unq.dapp.bolsa.trading.domain.OrderType;
import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import com.unq.dapp.bolsa.trading.infrastructure.OrderRepository;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final int TOKENS_PER_PURCHASE = 5;
    private static final int PLAYERS_PER_USER = 5;

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlayerStatsPort playerStatsPort;
    private final PlayerMetricsSnapshotRepository metricsRepository;
    private final PlayerTokenInventoryRepository inventoryRepository;
    private final QuoteRecalculationOrchestrator orchestrator;
    private final QuoteRepository quoteRepository;
    private final TokenHoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final StrategyConfigRepository strategyConfigRepository;
    private final String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           PlayerRepository playerRepository,
                           PasswordEncoder passwordEncoder,
                           PlayerStatsPort playerStatsPort,
                           PlayerMetricsSnapshotRepository metricsRepository,
                           PlayerTokenInventoryRepository inventoryRepository,
                           QuoteRecalculationOrchestrator orchestrator,
                           QuoteRepository quoteRepository,
                           TokenHoldingRepository holdingRepository,
                           OrderRepository orderRepository,
                           StrategyConfigRepository strategyConfigRepository,
                           @Value("${app.seed.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.playerStatsPort = playerStatsPort;
        this.metricsRepository = metricsRepository;
        this.inventoryRepository = inventoryRepository;
        this.orchestrator = orchestrator;
        this.quoteRepository = quoteRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.strategyConfigRepository = strategyConfigRepository;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedPlayers();
        seedStrategyConfigs();
        seedPlayerMetrics();
        seedInitialQuotes();
        seedQuoteHistory();
        seedTradingScenario();
    }

    private void seedUsers() {
        if (!userRepository.existsByEmail("system@bolsa.local")) {
            User admin = new User();
            admin.setEmail("system@bolsa.local");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("[DataInitializer] Usuario ADMIN creado: system@bolsa.local");
        }

        createUserIfNotExists("alice@example.com", "Alice1234");
        createUserIfNotExists("bob@example.com", "Bob1234");
        createUserIfNotExists("charlie@example.com", "Charlie1234");
        createUserIfNotExists("diana@example.com", "Diana1234");
    }

    private void createUserIfNotExists(String email, String password) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(Role.USER);
            userRepository.save(user);
            log.info("[DataInitializer] Usuario USER creado: {}", email);
        }
    }

    private void seedStrategyConfigs() {
        seedConfig("MatchMetrics",
                "{\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}");
        seedConfig("PositionWeighted",
                "{\"FW\":{\"goals\":0.6,\"assists\":0.3,\"rating\":0.1}," +
                "\"MF\":{\"goals\":0.2,\"assists\":0.5,\"rating\":0.3}," +
                "\"DF\":{\"goals\":0.1,\"assists\":0.2,\"rating\":0.7}," +
                "\"GK\":{\"goals\":0.0,\"assists\":0.0,\"rating\":1.0}}");
    }

    private void seedConfig(String name, String weightsJson) {
        if (strategyConfigRepository.findByName(name).isEmpty()) {
            StrategyConfig config = new StrategyConfig();
            config.setName(name);
            config.setWeightsJson(weightsJson);
            config.setActive(true);
            config.setConfigVersion(0);
            strategyConfigRepository.save(config);
            log.info("[DataInitializer] StrategyConfig creado: {}", name);
        }
    }

    private void seedPlayers() {
        if (playerRepository.count() > 0) return;

        List<Player> players = Arrays.stream(League.values())
                .flatMap(league -> {
                    List<ScrapedPlayer> scraped = playerStatsPort.fetchPlayersByLeague(league);
                    log.info("[DataInitializer] {} jugadores obtenidos para {}", scraped.size(), league);
                    return scraped.stream();
                })
                .map(this::toPlayer)
                .toList();

        playerRepository.saveAll(players);
        log.info("[DataInitializer] {} jugadores guardados en total", players.size());
    }

    private void seedPlayerMetrics() {
        List<Player> players = playerRepository.findAll();
        if (players.isEmpty()) return;

        LocalDate periodEnd = LocalDate.now(ZoneOffset.UTC);
        LocalDate periodStart = periodEnd.minusMonths(1);

        int created = 0;
        for (Player player : players) {
            if (metricsRepository.findTopByPlayerIdOrderByPeriodEndDesc(player.getId()).isPresent()) {
                continue;
            }
            metricsRepository.save(buildMetrics(player, periodStart, periodEnd));
            created++;
        }
        log.info("[DataInitializer] {} métricas de jugadores creadas", created);
    }

    private PlayerMetricsSnapshot buildMetrics(Player player, LocalDate periodStart, LocalDate periodEnd) {
        PlayerMetricsSnapshot m = new PlayerMetricsSnapshot();
        m.setPlayerId(player.getId());
        m.setPeriodStart(periodStart);
        m.setPeriodEnd(periodEnd);
        m.setMatches(12);
        m.setMinutesPlayed(1000);

        Position pos = player.getPosition() != null ? player.getPosition() : Position.MF;
        switch (pos) {
            case FW -> { m.setGoals(8); m.setAssists(4); m.setRating(BigDecimal.valueOf(7.5)); }
            case MF -> { m.setGoals(4); m.setAssists(8); m.setRating(BigDecimal.valueOf(7.3)); }
            case DF -> { m.setGoals(1); m.setAssists(3); m.setRating(BigDecimal.valueOf(7.0)); }
            case GK -> { m.setGoals(0); m.setAssists(0); m.setRating(BigDecimal.valueOf(7.2)); }
        }
        return m;
    }

    private void seedInitialQuotes() {
        List<Player> players = playerRepository.findAll();
        if (players.isEmpty()) return;

        for (Player player : players) {
            if (inventoryRepository.findById(player.getId()).isEmpty()) {
                PlayerTokenInventory inventory = new PlayerTokenInventory();
                inventory.setPlayerId(player.getId());
                inventory.setTotalEmitted(100);
                inventory.setHeldBySystem(100);
                inventory.setInitialTokenValue(BigDecimal.ONE);
                inventoryRepository.save(inventory);
            }
        }

        int total = orchestrator.recalculateAll(null);
        log.info("[DataInitializer] {} cotizaciones iniciales calculadas", total);
    }

    private void seedQuoteHistory() {
        long totalPlayers = playerRepository.count();
        if (totalPlayers == 0 || quoteRepository.count() > totalPlayers) return;

        LocalDateTime[] pastDates = {
            LocalDateTime.now(ZoneOffset.UTC).minusDays(30),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(15),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(7)
        };

        for (Player player : playerRepository.findAll()) {
            double[] values = historicalValuesFor(player.getPosition());
            for (int i = 0; i < pastDates.length; i++) {
                Quote quote = new Quote();
                quote.setPlayerId(player.getId());
                quote.setValue(new Money(BigDecimal.valueOf(values[i]), "CREDITS"));
                quote.setCalculatedAt(pastDates[i]);
                quote.setStrategyName("MatchMetrics");
                quote.setStrategyVersion("v1.0");
                quoteRepository.save(quote);
            }
        }
        log.info("[DataInitializer] Historial de cotizaciones creado para {} jugadores", totalPlayers);
    }

    private double[] historicalValuesFor(Position position) {
        if (position == null) position = Position.MF;
        return switch (position) {
            case FW -> new double[]{1.55, 1.65, 1.72};
            case MF -> new double[]{1.40, 1.48, 1.55};
            case DF -> new double[]{1.25, 1.32, 1.38};
            case GK -> new double[]{1.30, 1.36, 1.42};
        };
    }

    private void seedTradingScenario() {
        if (holdingRepository.count() > 0) return;

        List<Player> players = playerRepository.findAll();
        if (players.size() < 20) {
            log.info("[DataInitializer] Menos de 20 jugadores disponibles, se omite el escenario de trading");
            return;
        }

        String[] users = {"alice@example.com", "bob@example.com", "charlie@example.com", "diana@example.com"};
        for (int u = 0; u < users.length; u++) {
            final int startIdx = u * PLAYERS_PER_USER;
            userRepository.findByEmail(users[u]).ifPresent(user -> {
                for (int i = startIdx; i < startIdx + PLAYERS_PER_USER; i++) {
                    seedBuyOrder(user, players.get(i));
                }
                log.info("[DataInitializer] Compras iniciales creadas para {}", user.getUsername());
            });
        }
    }

    private void seedBuyOrder(User user, Player player) {
        PlayerTokenInventory inventory = inventoryRepository.findById(player.getId()).orElse(null);
        if (inventory == null || inventory.getHeldBySystem() < TOKENS_PER_PURCHASE) return;

        inventory.setHeldBySystem(inventory.getHeldBySystem() - TOKENS_PER_PURCHASE);
        inventoryRepository.save(inventory);

        TokenHolding holding = new TokenHolding();
        holding.setUserId(user.getId());
        holding.setPlayerId(player.getId());
        holding.setQuantity(TOKENS_PER_PURCHASE);
        holding.setAvgBuyPrice(BigDecimal.ONE);
        holdingRepository.save(holding);

        Order order = new Order();
        order.setUserId(user.getId());
        order.setPlayerId(player.getId());
        order.setType(OrderType.BUY);
        order.setQuantity(TOKENS_PER_PURCHASE);
        order.setUnitPrice(BigDecimal.ONE);
        order.setTotalAmount(BigDecimal.valueOf(TOKENS_PER_PURCHASE));
        order.setIdempotencyKey(
                UUID.nameUUIDFromBytes((user.getUsername() + ":" + player.getId()).getBytes()).toString());
        orderRepository.save(order);
    }

    private Player toPlayer(ScrapedPlayer scraped) {
        Player p = new Player();
        p.setExternalId(scraped.whoScoredId());
        p.setName(scraped.name());
        p.setPosition(scraped.position());
        p.setTeam(scraped.team());
        p.setLeague(scraped.league());
        p.setNationality(scraped.nationality());
        return p;
    }
}
