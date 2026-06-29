package com.unq.dapp.bolsa.portfolio.application;

import com.unq.dapp.bolsa.catalog.application.PlayerService;
import com.unq.dapp.bolsa.portfolio.api.PortfolioResponse;
import com.unq.dapp.bolsa.portfolio.api.PositionResponse;
import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.trading.domain.TokenHolding;
import com.unq.dapp.bolsa.trading.infrastructure.TokenHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final TokenHoldingRepository holdingRepository;
    private final QuoteService quoteService;
    private final PlayerService playerService;

    public PortfolioService(TokenHoldingRepository holdingRepository,
                            QuoteService quoteService,
                            PlayerService playerService) {
        this.holdingRepository = holdingRepository;
        this.quoteService = quoteService;
        this.playerService = playerService;
    }

    public PortfolioResponse getPortfolio(Long userId) {
        List<TokenHolding> holdings = holdingRepository.findAllByUserId(userId);

        List<PositionResponse> positions = holdings.stream()
                .map(h -> buildPosition(h))
                .toList();

        BigDecimal totalInvested = positions.stream()
                .map(PositionResponse::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrentValue = positions.stream()
                .map(PositionResponse::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvested);

        return new PortfolioResponse(userId, positions, totalInvested, totalCurrentValue, totalProfitLoss);
    }

    private PositionResponse buildPosition(TokenHolding holding) {
        String playerName = resolvePlayerName(holding.getPlayerId());
        BigDecimal currentTokenValue = resolveCurrentPrice(holding.getPlayerId());

        BigDecimal qty = BigDecimal.valueOf(holding.getQuantity());
        BigDecimal totalCost = holding.getAvgBuyPrice().multiply(qty);
        BigDecimal currentValue = currentTokenValue.multiply(qty);
        BigDecimal profitLoss = currentValue.subtract(totalCost);
        BigDecimal profitLossPct = totalCost.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : profitLoss.divide(totalCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        return new PositionResponse(
                holding.getPlayerId(),
                playerName,
                holding.getQuantity(),
                holding.getAvgBuyPrice(),
                currentTokenValue,
                currentValue,
                totalCost,
                profitLoss,
                profitLossPct
        );
    }

    private BigDecimal resolveCurrentPrice(Long playerId) {
        return quoteService.getCurrentQuote(playerId)
                .map(q -> q.getValue().amount())
                .orElse(BigDecimal.ZERO);
    }

    private String resolvePlayerName(Long playerId) {
        try {
            return playerService.findById(playerId).getName();
        } catch (Exception e) {
            return "Desconocido";
        }
    }
}
