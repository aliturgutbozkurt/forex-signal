package com.fsp.performance.api;

public record MonteCarloReportDto(
        int simulations,
        int tradeCount,
        double medianTerminalEquity,
        double p95MaxDrawdownPct,
        double p99MaxDrawdownPct,
        double probabilityOfProfitPct
) {}

