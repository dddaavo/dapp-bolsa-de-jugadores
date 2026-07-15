package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.catalog.domain.Position;

import java.math.BigDecimal;
import java.util.Map;

public class PositionWeightedStrategy implements PricingStrategy {

    private static final String NAME = "PositionMetrics";
    private static final String DEFAULT_VERSION = "v1.0";

    private static final double MAX_GOALS = 30.0;
    private static final double MAX_ASSISTS = 20.0;
    private static final double MAX_RATING = 10.0;

    private final Map<Position, StrategyWeights> customWeights;
    private final String strategyVersion;

    public PositionWeightedStrategy() {
        this.customWeights = null;
        this.strategyVersion = DEFAULT_VERSION;
    }

    public PositionWeightedStrategy(Map<Position, StrategyWeights> customWeights, String version) {
        this.customWeights = customWeights;
        this.strategyVersion = version;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return strategyVersion;
    }

    @Override
    public Money calculate(PlayerMetricsSnapshot snapshot, PricingContext context) {
        if (snapshot.getMatches() == 0) {
            return context.initialTokenValue();
        }

        double[] weights = weightsFor(context.position());
        double score = weights[0] * normalize(snapshot.getGoals(), MAX_GOALS)
                     + weights[1] * normalize(snapshot.getAssists(), MAX_ASSISTS)
                     + weights[2] * normalizeRating(snapshot.getRating());

        return context.initialTokenValue().multiply(1.0 + score);
    }

    private double[] weightsFor(Position position) {
        Position p = (position != null) ? position : Position.MF;
        if (customWeights != null && customWeights.containsKey(p)) {
            StrategyWeights w = customWeights.get(p);
            return new double[]{w.goals(), w.assists(), w.rating()};
        }
        return switch (p) {
            case FW -> new double[]{0.6, 0.3, 0.1};
            case MF -> new double[]{0.2, 0.5, 0.3};
            case DF -> new double[]{0.1, 0.2, 0.7};
            case GK -> new double[]{0.0, 0.0, 1.0};
        };
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
