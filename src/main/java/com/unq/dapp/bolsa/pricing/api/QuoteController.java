package com.unq.dapp.bolsa.pricing.api;

import com.unq.dapp.bolsa.catalog.application.PlayerService;
import com.unq.dapp.bolsa.pricing.application.QuoteService;
import com.unq.dapp.bolsa.pricing.domain.Quote;
import com.unq.dapp.bolsa.pricing.domain.QuoteNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Cotizaciones", description = "Cotizaciones de tokens de jugadores")
@RestController
@RequestMapping("/api/v1/players")
public class QuoteController {

    private final QuoteService quoteService;
    private final PlayerService playerService;

    public QuoteController(QuoteService quoteService, PlayerService playerService) {
        this.quoteService = quoteService;
        this.playerService = playerService;
    }

    @Operation(summary = "Cotización actual de un jugador")
    @ApiResponse(responseCode = "404", description = "Sin cotización para el jugador")
    @GetMapping("/{id}/quotes/current")
    public ResponseEntity<QuoteResponse> getCurrent(@PathVariable Long id) {
        return ResponseEntity.ok(
                quoteService.getCurrentQuote(id)
                        .map(QuoteResponse::from)
                        .orElseThrow(() -> new QuoteNotFoundException(id))
        );
    }

    @Operation(summary = "Cotización vigente de un jugador a una fecha dada")
    @ApiResponse(responseCode = "400", description = "Fecha inválida o ausente")
    @ApiResponse(responseCode = "404", description = "Sin cotización para el jugador a esa fecha")
    @GetMapping("/{id}/quotes/at")
    public ResponseEntity<QuoteResponse> getAt(
            @PathVariable Long id,
            @Parameter(description = "Fecha (yyyy-MM-dd)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(
                quoteService.getQuoteAt(id, date)
                        .map(QuoteResponse::from)
                        .orElseThrow(() -> new QuoteNotFoundException(id))
        );
    }

    @Operation(summary = "Historial de cotizaciones de un jugador")
    @GetMapping("/{id}/quotes")
    public ResponseEntity<List<QuoteResponse>> getHistory(
            @PathVariable Long id,
            @Parameter(description = "Fecha inicio (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha fin (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<Quote> quotes = (from != null && to != null)
                ? quoteService.getQuoteHistory(id, from, to)
                : quoteService.getAllQuotes(id);
        return ResponseEntity.ok(quotes.stream().map(QuoteResponse::from).toList());
    }

    @Operation(summary = "Ranking de jugadores por cotización actual")
    @GetMapping("/ranking")
    public ResponseEntity<List<PlayerRankingResponse>> getRanking(
            @Parameter(description = "Estrategia de cotización (opcional, default: más reciente)")
            @RequestParam(required = false) String strategy,
            @Parameter(description = "Cantidad de resultados (default 10, máx 50)")
            @RequestParam(defaultValue = "10") int limit
    ) {
        int safeLimit = Math.min(limit, 50);
        List<Quote> topQuotes = quoteService.getRankingQuotes(safeLimit, strategy);
        List<PlayerRankingResponse> ranking = new ArrayList<>();
        for (int i = 0; i < topQuotes.size(); i++) {
            Quote q = topQuotes.get(i);
            var player = playerService.findById(q.getPlayerId());
            ranking.add(new PlayerRankingResponse(
                    i + 1,
                    player.getId(),
                    player.getName(),
                    q.getValue().amount(),
                    q.getValue().currency()
            ));
        }
        return ResponseEntity.ok(ranking);
    }
}
