package com.unq.dapp.bolsa.trading.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SellRequest(
        @Schema(description = "ID del jugador cuyos tokens se venden", example = "1")
        @NotNull Long playerId,

        @Schema(description = "Cantidad de tokens a vender", example = "3")
        @NotNull @Min(1) Integer quantity
) {}
