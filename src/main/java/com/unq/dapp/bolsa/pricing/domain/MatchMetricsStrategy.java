package com.unq.dapp.bolsa.pricing.domain;

import java.math.BigDecimal;

/**
 * Estrategia de pricing basada en métricas de partidos.
 * Versión 1.0 - Normaliza métricas y calcula score ponderado.
 *
 * Fórmula: cotización = initialValue × (1 + score)
 * Score = weights.goals × norm(goals) + weights.assists × norm(assists) + weights.rating × norm(rating)
 */
public class MatchMetricsStrategy implements PricingStrategy {

    private static final String NAME = "MatchMetrics";
    private static final String VERSION = "v1.0";

    // Valores de normalización (máximos esperados)
    private static final double MAX_GOALS = 30.0;
    private static final double MAX_ASSISTS = 20.0;
    private static final double MAX_RATING = 10.0;

    private final StrategyWeights weights;

    public MatchMetricsStrategy(StrategyWeights weights) {
        this.weights = weights;
    }

    public MatchMetricsStrategy() {
        this(StrategyWeights.defaultWeights());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Money calculate(PlayerMetricsSnapshot snapshot, PricingContext context) {
        if (snapshot.getMatches() == 0) {
            // Sin partidos jugados, retorna valor inicial
            return context.initialTokenValue();
        }

        double normalizedGoals = normalize(snapshot.getGoals(), MAX_GOALS);
        double normalizedAssists = normalize(snapshot.getAssists(), MAX_ASSISTS);
        double normalizedRating = normalizeRating(snapshot.getRating());

        double score = (weights.goals() * normalizedGoals) +
                       (weights.assists() * normalizedAssists) +
                       (weights.rating() * normalizedRating);

        // Cotización = initialValue × (1 + score)
        double multiplier = 1.0 + score;
        return context.initialTokenValue().multiply(multiplier);
    }

    private double normalize(int value, double max) {
        return Math.min(value / max, 1.0);
    }

    private double normalizeRating(BigDecimal rating) {
        if (rating == null) {
            return 0.0;
        }
        return Math.min(rating.doubleValue() / MAX_RATING, 1.0);
    }
}

