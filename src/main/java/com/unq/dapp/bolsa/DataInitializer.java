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
import com.unq.dapp.bolsa.pricing.domain.PlayerMetricsSnapshot;
import com.unq.dapp.bolsa.pricing.domain.PlayerTokenInventory;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
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
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlayerStatsPort playerStatsPort;
    private final PlayerMetricsSnapshotRepository metricsRepository;
    private final PlayerTokenInventoryRepository inventoryRepository;
    private final QuoteRecalculationOrchestrator orchestrator;
    private final String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           PlayerRepository playerRepository,
                           PasswordEncoder passwordEncoder,
                           PlayerStatsPort playerStatsPort,
                           PlayerMetricsSnapshotRepository metricsRepository,
                           PlayerTokenInventoryRepository inventoryRepository,
                           QuoteRecalculationOrchestrator orchestrator,
                           @Value("${app.seed.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.playerStatsPort = playerStatsPort;
        this.metricsRepository = metricsRepository;
        this.inventoryRepository = inventoryRepository;
        this.orchestrator = orchestrator;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedPlayers();
        seedPlayerMetrics();
        seedInitialQuotes();
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
            PlayerMetricsSnapshot metrics = buildMetrics(player, periodStart, periodEnd);
            metricsRepository.save(metrics);
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

        Position pos = player.getPosition();
        if (pos == null) pos = Position.MF;

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
