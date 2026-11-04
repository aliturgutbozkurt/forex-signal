package com.fsp.shared;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A single indicator or rule vote in [-1.0, +1.0] with its category and base weight (SPEC-001 §5.1, §6.2).
 */
@ValueObject
public record Vote(
        String indicator,
        String category,
        Direction direction,
        double signedStrength,
        double weight,
        String summaryTr
) {

    public Vote {
        Objects.requireNonNull(indicator, "indicator must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(summaryTr, "summaryTr must not be null");
        if (indicator.isBlank()) {
            throw new IllegalArgumentException("indicator must not be blank");
        }
        if (!Double.isFinite(signedStrength) || signedStrength < -1.0 || signedStrength > 1.0) {
            throw new IllegalArgumentException("signedStrength must be in [-1.0, 1.0]: " + signedStrength);
        }
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException("weight must be non-negative: " + weight);
        }
    }
}

