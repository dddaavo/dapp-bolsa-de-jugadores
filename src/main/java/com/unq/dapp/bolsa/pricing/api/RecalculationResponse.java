package com.unq.dapp.bolsa.pricing.api;

import java.time.LocalDateTime;

public record RecalculationResponse(
        int totalRecalculated,
        String strategyUsed,
        LocalDateTime timestamp
) {}
