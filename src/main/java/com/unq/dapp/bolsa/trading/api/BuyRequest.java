package com.unq.dapp.bolsa.trading.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BuyRequest(
        @Schema(description = "ID del jugador cuyos tokens se compran", example = "1")
        @NotNull Long playerId,

        @Schema(description = "Cantidad de tokens a comprar", example = "5")
        @NotNull @Min(1) Integer quantity
) {}
