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

    public DataInitializer(UserRepository userRepository,
                           PlayerRepository playerRepository,
                           PasswordEncoder passwordEncoder,
                           PlayerStatsPort playerStatsPort) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.playerStatsPort = playerStatsPort;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdminUser();
        seedPlayers();
    }

    private void seedAdminUser() {
        if (!userRepository.existsByEmail("system@bolsa.local")) {
            User admin = new User();
            admin.setEmail("system@bolsa.local");
            admin.setPasswordHash(passwordEncoder.encode("admin1234"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
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
