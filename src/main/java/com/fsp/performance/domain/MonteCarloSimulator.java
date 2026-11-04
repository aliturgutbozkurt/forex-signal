package com.fsp.performance.domain;

import com.fsp.performance.api.MonteCarloReportDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deterministic Monte Carlo bootstrap/permutation simulator over trade R-multiples (SPEC-001 FR-12, Issue #36).
 */
public final class MonteCarloSimulator {

    public MonteCarloReportDto simulate(List<Double> rMultiples, int simulations, long seed) {
        if (rMultiples.isEmpty() || simulations <= 0) {
            return new MonteCarloReportDto(simulations, 0, 100.0, 0.0, 0.0, 0.0);
        }

        Random rng = new Random(seed);
        List<Double> terminalEquities = new ArrayList<>(simulations);
        List<Double> maxDrawdowns = new ArrayList<>(simulations);
        int profitableRuns = 0;

        List<Double> working = new ArrayList<>(rMultiples);
        for (int sim = 0; sim < simulations; sim++) {
            Collections.shuffle(working, rng);
            double equity = 100.0;
            double peak = 100.0;
            double maxDd = 0.0;
            for (double r : working) {
                // Resample with slight jitter to model execution variance
                double jitter = (rng.nextDouble() - 0.5) * 0.04;
                double effectiveR = r + jitter;
                equity *= (1.0 + effectiveR * 0.01);
                if (equity > peak) {
                    peak = equity;
                }
                double dd = peak > 0 ? ((peak - equity) / peak) * 100.0 : 0.0;
                if (dd > maxDd) {
                    maxDd = dd;
                }
            }
            terminalEquities.add(equity);
            maxDrawdowns.add(maxDd);
            if (equity > 100.0) {
                profitableRuns++;
            }
        }

        Collections.sort(terminalEquities);
        Collections.sort(maxDrawdowns);

        double medianEquity = terminalEquities.get(simulations / 2);
        double p95Dd = maxDrawdowns.get(Math.min(simulations - 1, (int) (simulations * 0.95)));
        double p99Dd = maxDrawdowns.get(Math.min(simulations - 1, (int) (simulations * 0.99)));
        double probProfit = (profitableRuns * 100.0) / simulations;

        return new MonteCarloReportDto(
                simulations,
                rMultiples.size(),
                round2(medianEquity),
                round2(p95Dd),
                round2(p99Dd),
                round2(probProfit)
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

