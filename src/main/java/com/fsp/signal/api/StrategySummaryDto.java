package com.fsp.signal.api;

public record StrategySummaryDto(
        String strategyId,
        String name,
        int version,
        double slAtrMultiplier,
        double tp1RiskMultiple,
        double tp2RiskMultiple,
        double tp3RiskMultiple,
        double minRiskReward,
        int cooldownBars,
        int maxHoldBars,
        double minRelativeVolume
) {}

