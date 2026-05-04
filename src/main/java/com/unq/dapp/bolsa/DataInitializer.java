package com.unq.dapp.bolsa;

import com.unq.dapp.bolsa.auth.domain.Role;
import com.unq.dapp.bolsa.auth.domain.User;
import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           PlayerRepository playerRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
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

        playerRepository.saveAll(List.of(
                player("Erling Haaland",      Position.FW, "Manchester City",        League.PREMIER_LEAGUE, "Norwegian"),
                player("Mohamed Salah",        Position.FW, "Liverpool",              League.PREMIER_LEAGUE, "Egyptian"),
                player("Phil Foden",           Position.MF, "Manchester City",        League.PREMIER_LEAGUE, "English"),
                player("Virgil van Dijk",      Position.DF, "Liverpool",              League.PREMIER_LEAGUE, "Dutch"),
                player("David Raya",           Position.GK, "Arsenal",                League.PREMIER_LEAGUE, "Spanish"),

                player("Harry Kane",           Position.FW, "Bayern Munich",          League.BUNDESLIGA,     "English"),
                player("Florian Wirtz",        Position.MF, "Bayer Leverkusen",       League.BUNDESLIGA,     "German"),
                player("Joshua Kimmich",       Position.MF, "Bayern Munich",          League.BUNDESLIGA,     "German"),
                player("Nico Schlotterbeck",   Position.DF, "Borussia Dortmund",      League.BUNDESLIGA,     "German"),
                player("Manuel Neuer",         Position.GK, "Bayern Munich",          League.BUNDESLIGA,     "German"),

                player("Vinícius Júnior",      Position.FW, "Real Madrid",            League.LA_LIGA,        "Brazilian"),
                player("Robert Lewandowski",   Position.FW, "FC Barcelona",           League.LA_LIGA,        "Polish"),
                player("Jude Bellingham",      Position.MF, "Real Madrid",            League.LA_LIGA,        "English"),
                player("Pedri",                Position.MF, "FC Barcelona",           League.LA_LIGA,        "Spanish"),
                player("Ter Stegen",           Position.GK, "FC Barcelona",           League.LA_LIGA,        "German"),

                player("Lautaro Martínez",     Position.FW, "Inter Milan",            League.SERIE_A,        "Argentine"),
                player("Nicolò Barella",       Position.MF, "Inter Milan",            League.SERIE_A,        "Italian"),
                player("Alessandro Bastoni",   Position.DF, "Inter Milan",            League.SERIE_A,        "Italian"),
                player("Federico Gatti",       Position.DF, "Juventus",               League.SERIE_A,        "Italian"),
                player("Mike Maignan",         Position.GK, "AC Milan",               League.SERIE_A,        "French"),

                player("Jonathan David",       Position.FW, "LOSC Lille",             League.LIGUE_1,        "Canadian"),
                player("Bradley Barcola",      Position.FW, "Paris Saint-Germain",    League.LIGUE_1,        "French"),
                player("Warren Zaïre-Emery",   Position.MF, "Paris Saint-Germain",    League.LIGUE_1,        "French"),
                player("Désiré Doué",          Position.MF, "Paris Saint-Germain",    League.LIGUE_1,        "French"),
                player("Gianluigi Donnarumma", Position.GK, "Paris Saint-Germain",    League.LIGUE_1,        "Italian")
        ));
    }

    private Player player(String name, Position position, String team, League league, String nationality) {
        Player p = new Player();
        p.setName(name);
        p.setPosition(position);
        p.setTeam(team);
        p.setLeague(league);
        p.setNationality(nationality);
        return p;
    }
}
