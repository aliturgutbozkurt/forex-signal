package com.fsp.shared;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Percentage(double value) implements Comparable<Percentage> {

    public Percentage {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Percentage must be a finite number");
        }
    }

    public static Percentage ofBounded(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(
                    "Percentage " + value + " out of bounds [" + min + ", " + max + "]");
        }
        return new Percentage(value);
    }

    public double asFraction() {
        return value / 100.0;
    }

    @Override
    public int compareTo(Percentage other) {
        return Double.compare(this.value, other.value);
    }
}

