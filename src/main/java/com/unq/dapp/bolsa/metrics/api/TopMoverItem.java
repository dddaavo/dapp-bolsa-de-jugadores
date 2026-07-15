package com.unq.dapp.bolsa.metrics.api;

import java.math.BigDecimal;

public record TopMoverItem(
        String playerName,
        BigDecimal cotizacionActual,
        BigDecimal cotizacionAnterior,
        String variacionPct,
        String tendencia
) {}
