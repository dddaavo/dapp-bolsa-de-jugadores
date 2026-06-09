package com.unq.dapp.bolsa;

import com.unq.dapp.bolsa.auth.infrastructure.UserRepository;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerMetricsSnapshotRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.PlayerTokenInventoryRepository;
import com.unq.dapp.bolsa.pricing.infrastructure.QuoteRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DataInitializerIT {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerMetricsSnapshotRepository metricsRepository;

    @Autowired
    private PlayerTokenInventoryRepository inventoryRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataInitializer dataInitializer;

    @BeforeAll
    void setup() throws Exception {
        if (playerRepository.count() == 0) {
            Player fw = new Player();
            fw.setName("Test FW Player");
            fw.setPosition(Position.FW);
            fw.setTeam("Test FC");
            fw.setLeague(League.PREMIER_LEAGUE);
            playerRepository.save(fw);

            Player df = new Player();
            df.setName("Test DF Player");
            df.setPosition(Position.DF);
            df.setTeam("Test FC");
            df.setLeague(League.LA_LIGA);
            playerRepository.save(df);
        }

        dataInitializer.run(null);
    }

    @Test
    void deberiaCrearMetricasParaTodosLosJugadores() {
        long players = playerRepository.count();
        assertThat(metricsRepository.count()).isGreaterThanOrEqualTo(players);
    }

    @Test
    void deberiaCrearInventarioParaTodosLosJugadores() {
        long players = playerRepository.count();
        assertThat(inventoryRepository.count()).isGreaterThanOrEqualTo(players);
    }

    @Test
    void deberiaCrearAlMenosUnaCotizacionPorJugador() {
        long players = playerRepository.count();
        assertThat(quoteRepository.count()).isGreaterThanOrEqualTo(players);
    }

    @Test
    void lasCotizacionesDebenTenerValorPositivo() {
        assertThat(quoteRepository.findAll())
                .isNotEmpty()
                .allSatisfy(q -> assertThat(q.getValue().amount())
                        .isGreaterThan(BigDecimal.ZERO));
    }

    @Test
    void deberiaCrearElAdminYLosUsuariosDePrueba() {
        assertThat(userRepository.existsByEmail("system@bolsa.local")).isTrue();
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("charlie@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("diana@example.com")).isTrue();
    }
}
