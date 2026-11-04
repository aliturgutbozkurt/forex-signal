package com.fsp.shared;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Confidence(int value) implements Comparable<Confidence> {

    public Confidence {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Confidence must be in 0..100: " + value);
        }
    }

    public static Confidence fromConfluenceScore(ConfluenceScore score) {
        int clamped = (int) Math.round(Math.min(100.0, Math.abs(score.value())));
        return new Confidence(clamped);
    }

    @Override
    public int compareTo(Confidence other) {
        return Integer.compare(this.value, other.value);
    }
}

