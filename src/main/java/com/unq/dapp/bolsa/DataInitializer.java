package com.unq.dapp.bolsa;

import com.unq.dapp.bolsa.auth.domain.Role;
import com.unq.dapp.bolsa.auth.domain.User;
import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlayerStatsPort playerStatsPort;
    private final String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           PlayerRepository playerRepository,
                           PasswordEncoder passwordEncoder,
                           PlayerStatsPort playerStatsPort,
                           @Value("${app.seed.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.playerStatsPort = playerStatsPort;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedPlayers();
    }

    private void seedUsers() {
        // Admin principal
        if (!userRepository.existsByEmail("system@bolsa.local")) {
            User admin = new User();
            admin.setEmail("system@bolsa.local");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("[DataInitializer] Usuario ADMIN creado: system@bolsa.local");
        }

        // 4 usuarios de prueba (requerido en E1)
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
