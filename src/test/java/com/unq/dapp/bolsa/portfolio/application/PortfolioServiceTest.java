package com.unq.dapp.bolsa.portfolio.application;

import com.unq.dapp.bolsa.catalog.application.PlayerService;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Player;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.portfolio.api.PortfolioResponse;
import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.pricing.domain.Money;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private TokenHoldingRepository holdingRepository;
    @Mock private QuoteService quoteService;
    @Mock private PlayerService playerService;

    private PortfolioService portfolioService;

    private static final Long USER_ID = 1L;
    private static final Long PLAYER_ID = 10L;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(holdingRepository, quoteService, playerService);
    }

    @Test
    void deberiaRetornarPortfolioConCalculosCorrectosDeGanancia() {
        TokenHolding holding = buildHolding(PLAYER_ID, 10, BigDecimal.valueOf(8.00));
        when(holdingRepository.findAllByUserId(USER_ID)).thenReturn(List.of(holding));
        when(quoteService.getCurrentQuote(PLAYER_ID)).thenReturn(Optional.of(buildQuote(PLAYER_ID, 10.00)));
        when(playerService.findById(PLAYER_ID)).thenReturn(buildPlayer(PLAYER_ID, "Haaland"));

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.userId()).isEqualTo(USER_ID);
        assertThat(portfolio.positions()).hasSize(1);

        var position = portfolio.positions().get(0);
        assertThat(position.playerName()).isEqualTo("Haaland");
        assertThat(position.quantity()).isEqualTo(10);
        assertThat(position.avgBuyPrice()).isEqualByComparingTo("8.00");
        assertThat(position.currentTokenValue()).isEqualByComparingTo("10.00");
        // totalCost = 10 * 8 = 80; currentValue = 10 * 10 = 100; profit = 20; pct = 25%
        assertThat(position.totalCost()).isEqualByComparingTo("80.00");
        assertThat(position.currentValue()).isEqualByComparingTo("100.00");
        assertThat(position.profitLoss()).isEqualByComparingTo("20.00");
        assertThat(position.profitLossPct()).isEqualByComparingTo("25.00");

        assertThat(portfolio.totalInvested()).isEqualByComparingTo("80.00");
        assertThat(portfolio.totalCurrentValue()).isEqualByComparingTo("100.00");
        assertThat(portfolio.totalProfitLoss()).isEqualByComparingTo("20.00");
    }

    @Test
    void deberiaRetornarPortfolioVacioCuandoNoTieneHoldings() {
        when(holdingRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.totalInvested()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(portfolio.totalCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(portfolio.totalProfitLoss()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deberiaUsarCeroComoValorCuandoJugadorSinCotizacion() {
        TokenHolding holding = buildHolding(PLAYER_ID, 5, BigDecimal.valueOf(10.00));
        when(holdingRepository.findAllByUserId(USER_ID)).thenReturn(List.of(holding));
        when(quoteService.getCurrentQuote(PLAYER_ID)).thenReturn(Optional.empty());
        when(playerService.findById(PLAYER_ID)).thenReturn(buildPlayer(PLAYER_ID, "Sin cotización"));

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        var position = portfolio.positions().get(0);
        assertThat(position.currentTokenValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(position.currentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(position.profitLoss()).isEqualByComparingTo("-50.00");
    }

    // --- helpers ---

    private TokenHolding buildHolding(Long playerId, int quantity, BigDecimal avgBuyPrice) {
        TokenHolding h = new TokenHolding();
        h.setUserId(USER_ID);
        h.setPlayerId(playerId);
        h.setQuantity(quantity);
        h.setAvgBuyPrice(avgBuyPrice);
        return h;
    }

    private Quote buildQuote(Long playerId, double value) {
        Quote q = new Quote();
        q.setPlayerId(playerId);
        q.setValue(new Money(BigDecimal.valueOf(value), "CREDITS"));
        return q;
    }

    private Player buildPlayer(Long id, String name) {
        Player p = new Player();
        p.setId(id);
        p.setName(name);
        p.setPosition(Position.FW);
        p.setTeam("Team");
        p.setLeague(League.PREMIER_LEAGUE);
        p.setActive(true);
        return p;
    }
}
