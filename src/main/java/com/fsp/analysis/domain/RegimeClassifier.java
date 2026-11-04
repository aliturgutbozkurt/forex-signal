package com.fsp.analysis.domain;

import com.fsp.shared.MarketRegime;
import java.util.Objects;

/**
 * Stateful market regime classifier using ADX, +DI/-DI, ATR%, EMA alignment, and BB width
 * with hysteresis bands so regimes do not flicker on boundary oscillations (SPEC-001 §5.1, Issue #19).
 */
public final class RegimeClassifier {

    private static final double ADX_ENTER_TREND = 25.0;
    private static final double ADX_EXIT_TREND = 20.0;
    private static final double ATR_PCT_ENTER_VOLATILE = 2.6;
    private static final double ATR_PCT_EXIT_VOLATILE = 2.0;
    private static final double BB_WIDTH_ENTER_VOLATILE = 0.065;
    private static final double BB_WIDTH_EXIT_VOLATILE = 0.045;

    private MarketRegime currentRegime = MarketRegime.RANGING;

    public MarketRegime currentRegime() {
        return currentRegime;
    }

    public MarketRegime classify(
            double adx,
            double plusDi,
            double minusDi,
            double atrPercent,
            double bbWidth,
            double ema21,
            double ema50
    ) {
        // 1. Check VOLATILE with hysteresis
        if (currentRegime == MarketRegime.VOLATILE) {
            if (atrPercent >= ATR_PCT_EXIT_VOLATILE || bbWidth >= BB_WIDTH_EXIT_VOLATILE) {
                return currentRegime;
            }
        } else if (atrPercent >= ATR_PCT_ENTER_VOLATILE || bbWidth >= BB_WIDTH_ENTER_VOLATILE) {
            currentRegime = MarketRegime.VOLATILE;
            return currentRegime;
        }

        // 2. Check TRENDING with hysteresis
        boolean wasTrending = currentRegime.isTrending();
        double adxThreshold = wasTrending ? ADX_EXIT_TREND : ADX_ENTER_TREND;

        if (adx >= adxThreshold) {
            boolean bullish = plusDi >= minusDi && ema21 >= ema50 * 0.9995;
            boolean bearish = minusDi > plusDi && ema21 <= ema50 * 1.0005;
            if (bullish) {
                currentRegime = MarketRegime.TRENDING_UP;
            } else if (bearish) {
                currentRegime = MarketRegime.TRENDING_DOWN;
            } else if (!wasTrending) {
                currentRegime = plusDi >= minusDi ? MarketRegime.TRENDING_UP : MarketRegime.TRENDING_DOWN;
            }
            return currentRegime;
        }

        currentRegime = MarketRegime.RANGING;
        return currentRegime;
    }

    public void reset(MarketRegime initial) {
        this.currentRegime = Objects.requireNonNull(initial);
    }
}

