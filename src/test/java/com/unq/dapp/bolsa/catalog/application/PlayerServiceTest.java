package com.unq.dapp.bolsa.catalog.application;

import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.PlayerNotFoundException;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.catalog.infrastructure.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    PlayerRepository playerRepository;

    @InjectMocks
    PlayerService playerService;

    @Test
    void deberiaRetornarJugadoresPaginados() {
        Pageable pageable = PageRequest.of(0, 10);
        Player player = buildPlayer(1L, "Erling Haaland", Position.FW, "Manchester City", League.PREMIER_LEAGUE);
        when(playerRepository.findWithFilters(any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(player)));

        Page<Player> result = playerService.list(null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Erling Haaland");
    }

    @Test
    void deberiaDelegarFiltroLigaAlRepositorio() {
        Pageable pageable = PageRequest.of(0, 10);
        Player player = buildPlayer(2L, "Vinícius Júnior", Position.FW, "Real Madrid", League.LA_LIGA);
        when(playerRepository.findWithFilters(eq(League.LA_LIGA), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(player)));

        Page<Player> result = playerService.list(League.LA_LIGA, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLeague()).isEqualTo(League.LA_LIGA);
    }

    @Test
    void deberiaRetornarJugadorCuandoExisteElId() {
        Player player = buildPlayer(1L, "Erling Haaland", Position.FW, "Manchester City", League.PREMIER_LEAGUE);
        when(playerRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(player));

        Player result = playerService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Erling Haaland");
    }

    @Test
    void deberiaLanzarExcepcionCuandoJugadorNoExiste() {
        when(playerRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.findById(99L))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessageContaining("99");
    }

    private Player buildPlayer(Long id, String name, Position position, String team, League league) {
        Player p = new Player();
        p.setId(id);
        p.setName(name);
        p.setPosition(position);
        p.setTeam(team);
        p.setLeague(league);
        p.setActive(true);
        return p;
    }
}
