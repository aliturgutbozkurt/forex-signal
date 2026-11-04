package com.fsp.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RiskReward(double ratio) implements Comparable<RiskReward> {

    public RiskReward {
        if (!Double.isFinite(ratio) || ratio <= 0.0) {
            throw new IllegalArgumentException("RiskReward ratio must be finite and positive: " + ratio);
        }
    }

    public static RiskReward of(Price entry, Price stopLoss, Price takeProfit) {
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(stopLoss, "stopLoss must not be null");
        Objects.requireNonNull(takeProfit, "takeProfit must not be null");
        BigDecimal risk = entry.absDistanceTo(stopLoss);
        BigDecimal reward = entry.absDistanceTo(takeProfit);
        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Risk distance cannot be zero");
        }
        double r = reward.divide(risk, 6, RoundingMode.HALF_UP).doubleValue();
        return new RiskReward(r);
    }

    @Override
    public int compareTo(RiskReward other) {
        return Double.compare(this.ratio, other.ratio);
    }
}

