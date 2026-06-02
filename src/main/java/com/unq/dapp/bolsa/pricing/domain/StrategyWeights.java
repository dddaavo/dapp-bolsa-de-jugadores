package com.unq.dapp.bolsa.pricing.domain;

/**
 * Value Object que encapsula los pesos configurables de una estrategia de pricing.
 */
public record StrategyWeights(double goals, double assists, double rating) {

    public StrategyWeights {
        if (goals < 0 || assists < 0 || rating < 0) {
            throw new IllegalArgumentException("Weights cannot be negative");
        }
        double total = goals + assists + rating;
        if (Math.abs(total - 1.0) > 0.01) {
            throw new IllegalArgumentException("Weights must sum to 1.0 (got: " + total + ")");
        }
    }

    public static StrategyWeights defaultWeights() {
        return new StrategyWeights(0.4, 0.3, 0.3);
    }
}

