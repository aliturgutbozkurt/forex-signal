package com.fsp.performance.domain;

import com.fsp.performance.api.PerformanceMetricsDto;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain calculator for trading performance metrics and SPEC-001 §9 publication gates:
 * <ul>
 *   <li>Win rate (TP1) &ge; 55%</li>
 *   <li>Profit factor &ge; 1.5</li>
 *   <li>Expectancy &gt; 0.25R / trade</li>
 *   <li>Max drawdown &le; 15% (at 1% fixed risk per trade)</li>
 *   <li>IS &rarr; OOS Profit Factor degradation &le; 30%</li>
 *   <li>Sample size &ge; 200 trades (otherwise automatically labeled {@code EXPERIMENTAL})</li>
 * </ul>
 */
public final class MetricsCalculator {

    private static final double RISK_PER_TRADE_PCT = 1.0;

    public PerformanceMetricsDto compute(
            String scopeLabel,
            List<Double> rMultiples,
            double outOfSampleDegradationPct
    ) {
        int total = rMultiples.size();
        int wins = 0;
        int losses = 0;
        double grossProfitR = 0.0;
        double grossLossR = 0.0;
        double sumR = 0.0;

        List<Double> equityCurve = new ArrayList<>(total + 1);
        double equity = 100.0;
        double peakEquity = 100.0;
        double maxDrawdownPct = 0.0;
        equityCurve.add(equity);

        for (double r : rMultiples) {
            if (r > 0.0) {
                wins++;
                grossProfitR += r;
            } else if (r < 0.0) {
                losses++;
                grossLossR += Math.abs(r);
            }
            sumR += r;
            // Fixed 1% risk compounding equity curve
            equity = equity * (1.0 + (r * (RISK_PER_TRADE_PCT / 100.0)));
            equityCurve.add(Math.round(equity * 100.0) / 100.0);
            if (equity > peakEquity) {
                peakEquity = equity;
            }
            double dd = peakEquity > 0 ? ((peakEquity - equity) / peakEquity) * 100.0 : 0.0;
            if (dd > maxDrawdownPct) {
                maxDrawdownPct = dd;
            }
        }

        double winRatePct = total > 0 ? (wins * 100.0) / total : 0.0;
        double profitFactor = grossLossR > 1e-9 ? grossProfitR / grossLossR : (grossProfitR > 0 ? 9.99 : 0.0);
        double expectancyR = total > 0 ? sumR / total : 0.0;

        // Sharpe & Sortino over R-multiples
        double sharpe = 0.0;
        double sortino = 0.0;
        if (total > 1) {
            double variance = 0.0;
            double downsideVariance = 0.0;
            for (double r : rMultiples) {
                double diff = r - expectancyR;
                variance += diff * diff;
                if (r < 0.0) {
                    downsideVariance += r * r;
                }
            }
            double stdDev = Math.sqrt(variance / (total - 1));
            double downsideDev = Math.sqrt(downsideVariance / total);
            sharpe = stdDev > 1e-9 ? (expectancyR / stdDev) * Math.sqrt(252.0) : 0.0;
            sortino = downsideDev > 1e-9 ? (expectancyR / downsideDev) * Math.sqrt(252.0) : 0.0;
        }

        // SPEC-001 §9 publication gate
        boolean passesAllGates = total >= 200
                && winRatePct >= 55.0
                && profitFactor >= 1.5
                && expectancyR > 0.25
                && maxDrawdownPct <= 15.0
                && outOfSampleDegradationPct <= 30.0;

        boolean experimental = !passesAllGates;
        String badge = experimental ? "EXPERIMENTAL" : "VERIFIED";

        return new PerformanceMetricsDto(
                scopeLabel,
                total,
                wins,
                losses,
                round2(winRatePct),
                round2(profitFactor),
                round2(expectancyR),
                round2(maxDrawdownPct),
                round2(sharpe),
                round2(sortino),
                round2(outOfSampleDegradationPct),
                experimental,
                badge,
                List.copyOf(equityCurve)
        );
    }

    public double computeProfitFactor(List<Double> rMultiples) {
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        for (double r : rMultiples) {
            if (r > 0) grossProfit += r;
            else if (r < 0) grossLoss += Math.abs(r);
        }
        return grossLoss > 1e-9 ? grossProfit / grossLoss : (grossProfit > 0 ? 5.0 : 0.0);
    }

    private static double round2(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }
}

