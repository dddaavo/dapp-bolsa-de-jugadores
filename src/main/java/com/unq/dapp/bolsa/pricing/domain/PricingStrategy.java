package com.unq.dapp.bolsa.pricing.domain;

/**
 * Strategy pattern para cálculo de cotizaciones.
 * Cada estrategia implementa una lógica diferente de pricing.
 */
public interface PricingStrategy {

    /**
     * Nombre único de la estrategia (ej: "GlobalMetrics")
     */
    String name();

    /**
     * Versión de la estrategia para auditoría (ej: "v1.0")
     */
    String version();

    /**
     * Calcula el valor de un token dado un snapshot de métricas.
     *
     * @param snapshot Métricas del jugador
     * @param context Contexto con información adicional (valor inicial,etc.)
     * @return Valor calculado del token
     */
    Money calculate(PlayerMetricsSnapshot snapshot, PricingContext context);
}

