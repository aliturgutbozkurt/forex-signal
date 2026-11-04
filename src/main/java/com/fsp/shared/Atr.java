package com.fsp.shared;

import java.math.BigDecimal;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Atr(BigDecimal value) {

    public Atr {
        Objects.requireNonNull(value, "ATR value must not be null");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("ATR value must be strictly positive: " + value);
        }
    }

    public static Atr of(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("ATR must be finite and positive: " + value);
        }
        return new Atr(BigDecimal.valueOf(value));
    }

    public BigDecimal multipliedBy(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("Factor must be positive: " + factor);
        }
        return value.multiply(BigDecimal.valueOf(factor));
    }
}

