package com.fsp.performance.api;

public record CostModel(
        double spreadBps,
        double commissionBps,
        double slippageBps
) {
    public CostModel {
        if (spreadBps < 0 || commissionBps < 0 || slippageBps < 0) {
            throw new IllegalArgumentException("Cost model basis points must be non-negative");
        }
    }

    public static CostModel realisticDefault() {
        return new CostModel(1.5, 1.0, 0.5); // 3 bps round-trip total cost
    }

    public double totalCostBps() {
        return spreadBps + commissionBps + slippageBps;
    }

    public double rPenaltyForRiskFraction(double riskFractionOfPrice) {
        if (riskFractionOfPrice <= 1e-9) {
            return 0.05;
        }
        double costFraction = totalCostBps() / 10_000.0;
        return costFraction / riskFractionOfPrice;
    }
}

