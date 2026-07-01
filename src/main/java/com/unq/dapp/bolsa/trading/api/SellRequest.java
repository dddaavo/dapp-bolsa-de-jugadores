package com.unq.dapp.bolsa.trading.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SellRequest(
        @NotNull Long playerId,
        @NotNull @Min(1) Integer quantity
) {}
