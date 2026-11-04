package com.fsp.signal.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record RiskPolicy(
        double slAtrMultiplier,
        double tp1RiskMultiple,
        double tp2RiskMultiple,
        double tp3RiskMultiple,
        double minRiskReward,
        int cooldownBars,
        int maxHoldBars,
        double minRelativeVolume
) {

    public RiskPolicy {
        if (slAtrMultiplier <= 0.0) {
            throw new IllegalArgumentException("slAtrMultiplier must be positive");
        }
        if (!(tp1RiskMultiple < tp2RiskMultiple && tp2RiskMultiple < tp3RiskMultiple)) {
            throw new IllegalArgumentException("TP multiples must satisfy TP1 < TP2 < TP3");
        }
        if (minRiskReward <= 0.0 || tp1RiskMultiple < minRiskReward) {
            throw new IllegalArgumentException("tp1RiskMultiple must meet minRiskReward");
        }
        if (cooldownBars < 1) {
            throw new IllegalArgumentException("cooldownBars must be >= 1 (INV-SIG-2)");
        }
        if (maxHoldBars < 1) {
            throw new IllegalArgumentException("maxHoldBars must be >= 1");
        }
        if (minRelativeVolume < 0.0) {
            throw new IllegalArgumentException("minRelativeVolume must be non-negative");
        }
    }

    public static RiskPolicy standard() {
        // SPEC-001 §7.4: SL = 1.5*ATR, TP1/2/3 = 1.5R/2.5R/4R, RVOL >= 1.2
        return new RiskPolicy(1.5, 1.5, 2.5, 4.0, 1.5, 3, 24, 1.2);
    }
}

