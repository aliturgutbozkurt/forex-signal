package com.fsp.analysis.api;

import com.fsp.shared.MarketRegime;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;

public record IndicatorSnapshotDto(
        Symbol symbol,
        Timeframe timeframe,
        long barIndex,
        Instant closeTime,
        double close,
        // Trend group
        double ema9,
        double ema21,
        double ema50,
        double ema200,
        double sma20,
        double hma16,
        double ichimokuTenkan,
        double ichimokuKijun,
        double ichimokuSpanA,
        double ichimokuSpanB,
        double supertrend,
        boolean supertrendBullish,
        double parabolicSar,
        boolean psarBullish,
        double adx14,
        double plusDi14,
        double minusDi14,
        // Momentum group
        double rsi14,
        double stochK,
        double stochD,
        double stochRsi,
        double macdLine,
        double macdSignal,
        double macdHistogram,
        double cci20,
        double williamsR14,
        double momentum10,
        double awesomeOscillator,
        double ultimateOscillator,
        DivergenceType rsiDivergence,
        DivergenceType macdDivergence,
        // Volatility & Volume group
        double atr14,
        double atrPercent,
        double bbUpper,
        double bbMiddle,
        double bbLower,
        double bbWidth,
        boolean bbSqueeze,
        double keltnerUpper,
        double keltnerLower,
        double donchianUpper,
        double donchianLower,
        double relativeVolume,
        double obv,
        double vwap,
        double vwapUpperBand,
        double vwapLowerBand,
        double cmf20,
        double mfi14,
        double accumulationDistribution,
        double volumeProfilePoc,
        double volumeProfileVah,
        double volumeProfileVal,
        // Structure & Regime
        PivotLevels pivots,
        MarketRegime regime
) {}

