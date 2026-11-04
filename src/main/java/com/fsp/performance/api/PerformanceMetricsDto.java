package com.fsp.performance.api;

import java.util.List;

public record PerformanceMetricsDto(
        String scopeLabel,
        int totalTrades,
        int winningTrades,
        int losingTrades,
        double winRatePct,
        double profitFactor,
        double expectancyR,
        double maxDrawdownPct,
        double sharpeRatio,
        double sortinoRatio,
        double outOfSampleDegradationPct,
        boolean experimental,
        String qualityBadge,
        List<Double> equityCurve
) {}

