package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.catalog.application.PlayerService;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.domain.QuoteNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuoteController.class)
@MockBean(JpaMetamodelMappingContext.class)
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuoteService quoteService;

    @MockBean
    private PlayerService playerService;

    @MockBean
    private com.unq.dapp.bolsa.auth.application.JwtService jwtService;

    @MockBean
    private com.unq.dapp.bolsa.auth.infrastructure.CustomUserDetailsService customUserDetailsService;

    private Quote buildQuote(Long playerId, double value) {
        Quote q = new Quote();
        q.setPlayerId(playerId);
        q.setValue(new Money(BigDecimal.valueOf(value), "CREDITS"));
        q.setCalculatedAt(LocalDateTime.of(2026, Month.JUNE, 1, 10, 0));
        q.setStrategyName("matchMetrics");
        q.setStrategyVersion("1.0");
        return q;
    }

    private Player buildPlayer(Long id, String name) {
        Player p = new Player();
        p.setId(id);
        p.setName(name);
        p.setPosition(Position.FW);
        p.setTeam("Team");
        p.setLeague(League.PREMIER_LEAGUE);
        return p;
    }

    @Test
    @WithMockUser
    void deberiaRetornarCotizacionActual() throws Exception {
        when(quoteService.getCurrentQuote(1L)).thenReturn(Optional.of(buildQuote(1L, 12.50)));

        mockMvc.perform(get("/api/v1/players/1/quotes/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(1))
                .andExpect(jsonPath("$.value").value(12.50))
                .andExpect(jsonPath("$.strategyName").value("matchMetrics"));
    }

    @Test
    @WithMockUser
    void deberiaRetornar404SiNoHayCotizacion() throws Exception {
        when(quoteService.getCurrentQuote(99L)).thenThrow(new QuoteNotFoundException(99L));

        mockMvc.perform(get("/api/v1/players/99/quotes/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUOTE_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void deberiaRetornarHistorialCompleto() throws Exception {
        when(quoteService.getAllQuotes(1L)).thenReturn(List.of(buildQuote(1L, 12.50), buildQuote(1L, 10.00)));

        mockMvc.perform(get("/api/v1/players/1/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser
    void deberiaRetornarHistorialFiltradoPorFecha() throws Exception {
        when(quoteService.getQuoteHistory(eq(1L), any(), any())).thenReturn(List.of(buildQuote(1L, 12.50)));

        mockMvc.perform(get("/api/v1/players/1/quotes?from=2026-01-01&to=2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser
    void deberiaRetornarRankingOrdenadoPorValor() throws Exception {
        when(quoteService.getRankingQuotes(10, null)).thenReturn(
                List.of(buildQuote(1L, 15.00), buildQuote(2L, 10.00))
        );
        when(playerService.findById(1L)).thenReturn(buildPlayer(1L, "Haaland"));
        when(playerService.findById(2L)).thenReturn(buildPlayer(2L, "Mbappé"));

        mockMvc.perform(get("/api/v1/players/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rankPosition").value(1))
                .andExpect(jsonPath("$[0].playerName").value("Haaland"))
                .andExpect(jsonPath("$[1].rankPosition").value(2))
                .andExpect(jsonPath("$[1].playerName").value("Mbappé"));
    }

    @Test
    @WithMockUser
    void deberiaLimitarRankingA50() throws Exception {
        when(quoteService.getRankingQuotes(50, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/players/ranking?limit=200"))
                .andExpect(status().isOk());
    }
}
