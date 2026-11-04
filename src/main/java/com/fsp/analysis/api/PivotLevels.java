package com.fsp.analysis.api;

import com.fsp.shared.Direction;

public record PivotLevels(
        double pivot,
        double classicR1,
        double classicR2,
        double classicR3,
        double classicS1,
        double classicS2,
        double classicS3,
        double fibR1,
        double fibR2,
        double fibS1,
        double fibS2,
        double camR3,
        double camR4,
        double camS3,
        double camS4,
        double swingHigh,
        double swingLow
) {

    /**
     * Snaps a candidate stop-loss level to nearby structural support/resistance if within tolerance (15% of risk distance).
     */
    public double snapStopLoss(Direction direction, double entry, double rawStopLoss, double atr) {
        double maxSnapDist = atr * 0.35;
        if (direction == Direction.BUY) {
            double[] supports = {classicS1, fibS1, camS3, swingLow};
            for (double s : supports) {
                if (s > 0 && s < entry && Math.abs(s - rawStopLoss) <= maxSnapDist) {
                    // Place slightly below structural support
                    return s - (atr * 0.05);
                }
            }
        } else {
            double[] resistances = {classicR1, fibR1, camR3, swingHigh};
            for (double r : resistances) {
                if (r > entry && Math.abs(r - rawStopLoss) <= maxSnapDist) {
                    // Place slightly above structural resistance
                    return r + (atr * 0.05);
                }
            }
        }
        return rawStopLoss;
    }

    /**
     * Snaps a candidate take-profit level to nearby structural target if within tolerance.
     */
    public double snapTakeProfit(Direction direction, double entry, double rawTakeProfit, double atr) {
        double maxSnapDist = atr * 0.40;
        if (direction == Direction.BUY) {
            double[] targets = {classicR1, fibR1, camR3, classicR2, fibR2, camR4, swingHigh};
            for (double t : targets) {
                if (t > entry && Math.abs(t - rawTakeProfit) <= maxSnapDist) {
                    return t;
                }
            }
        } else {
            double[] targets = {classicS1, fibS1, camS3, classicS2, fibS2, camS4, swingLow};
            for (double t : targets) {
                if (t > 0 && t < entry && Math.abs(t - rawTakeProfit) <= maxSnapDist) {
                    return t;
                }
            }
        }
        return rawTakeProfit;
    }
}

