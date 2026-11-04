package com.fsp.shared;

import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record ConfluenceScore(double value) implements Comparable<ConfluenceScore> {

    public ConfluenceScore {
        if (!Double.isFinite(value) || value < -100.0 || value > 100.0) {
            throw new IllegalArgumentException("ConfluenceScore must be in [-100, 100]: " + value);
        }
    }

    public double abs() {
        return Math.abs(value);
    }

    public Optional<Direction> direction() {
        if (value > 0.0) {
            return Optional.of(Direction.BUY);
        }
        if (value < 0.0) {
            return Optional.of(Direction.SELL);
        }
        return Optional.empty();
    }

    @Override
    public int compareTo(ConfluenceScore other) {
        return Double.compare(this.value, other.value);
    }
}

