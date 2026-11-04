package com.fsp.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Price(BigDecimal amount, Currency currency) implements Comparable<Price> {

    public Price {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price amount must be strictly positive: " + amount);
        }
        amount = amount.stripTrailingZeros();
        if (amount.scale() < 0) {
            amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        }
    }

    public static Price of(double value, Currency currency) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Price must be finite and positive: " + value);
        }
        return new Price(BigDecimal.valueOf(value), currency);
    }

    public static Price of(String value, Currency currency) {
        return new Price(new BigDecimal(value), currency);
    }

    public Price add(Price other) {
        requireSameCurrency(other);
        return new Price(this.amount.add(other.amount), this.currency);
    }

    public Price addDelta(BigDecimal delta) {
        Objects.requireNonNull(delta, "delta must not be null");
        BigDecimal next = this.amount.add(delta);
        if (next.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Resulting price must remain positive: " + next);
        }
        return new Price(next, this.currency);
    }

    public BigDecimal differenceFrom(Price other) {
        requireSameCurrency(other);
        return this.amount.subtract(other.amount);
    }

    public BigDecimal absDistanceTo(Price other) {
        requireSameCurrency(other);
        return this.amount.subtract(other.amount).abs();
    }

    public Price snapToTick(BigDecimal tickSize) {
        Objects.requireNonNull(tickSize, "tickSize must not be null");
        if (tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("tickSize must be positive");
        }
        BigDecimal steps = this.amount.divide(tickSize, 0, RoundingMode.HALF_UP);
        BigDecimal snapped = steps.multiply(tickSize);
        if (snapped.compareTo(BigDecimal.ZERO) <= 0) {
            snapped = tickSize;
        }
        return new Price(snapped, this.currency);
    }

    public double doubleValue() {
        return amount.doubleValue();
    }

    private void requireSameCurrency(Price other) {
        Objects.requireNonNull(other, "other price must not be null");
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public int compareTo(Price other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }
}

