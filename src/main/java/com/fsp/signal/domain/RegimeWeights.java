package com.fsp.signal.domain;

import com.fsp.shared.MarketRegime;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RegimeWeights(
        double trendWeight,
        double momentumWeight,
        double volatilityWeight,
        double volumeWeight,
        double structureWeight,
        double scannerWeight,
        double entryThreshold
) {

    public RegimeWeights {
        if (trendWeight < 0 || momentumWeight < 0 || volatilityWeight < 0
                || volumeWeight < 0 || structureWeight < 0 || scannerWeight < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        if (entryThreshold <= 0 || entryThreshold > 100.0) {
            throw new IllegalArgumentException("entryThreshold must be in (0, 100]");
        }
    }

    public static RegimeWeights forRegime(MarketRegime regime) {
        Objects.requireNonNull(regime, "regime must not be null");
        return switch (regime) {
            case TRENDING_UP, TRENDING_DOWN -> new RegimeWeights(1.6, 1.1, 0.9, 1.2, 1.0, 0.8, 32.0);
            case RANGING -> new RegimeWeights(0.7, 1.6, 1.3, 1.1, 1.4, 0.7, 38.0);
            case VOLATILE -> new RegimeWeights(1.2, 1.0, 1.6, 1.5, 1.2, 0.6, 44.0);
        };
    }

    public double weightForCategory(String category) {
        return switch (category) {
            case "TREND" -> trendWeight;
            case "MOMENTUM" -> momentumWeight;
            case "VOLATILITY" -> volatilityWeight;
            case "VOLUME" -> volumeWeight;
            case "STRUCTURE" -> structureWeight;
            case "SCANNER" -> scannerWeight;
            default -> 1.0;
        };
    }
}

