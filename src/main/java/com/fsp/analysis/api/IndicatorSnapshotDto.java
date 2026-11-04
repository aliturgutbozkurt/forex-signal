package com.fsp.analysis.api;

import com.fsp.shared.MarketRegime;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

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
) {

    public record IndicatorRowVerdict(
            String label,
            String valueText,
            String action,
            String badgeClass
    ) {}

    public List<IndicatorRowVerdict> indicatorVerdictRows() {
        Locale tr = Locale.forLanguageTag("tr-TR");
        return List.of(
                row("EMA 9 / 21 / 50",
                        String.format(tr, "%.4f / %.4f / %.4f", ema9, ema21, ema50),
                        (ema9 > ema21 && ema21 > ema50) ? 1 : ((ema9 < ema21 && ema21 < ema50) ? -1 : 0)),
                row("EMA 200 / SMA 20 / HMA 16",
                        String.format(tr, "%.4f / %.4f / %.4f", ema200, sma20, hma16),
                        (close > ema200 && hma16 > sma20) ? 1 : ((close < ema200 && hma16 < sma20) ? -1 : 0)),
                row("Supertrend (10,3)",
                        String.format(tr, "%.4f (%s)", supertrend, supertrendBullish ? "Pozitif" : "Negatif"),
                        supertrendBullish ? 1 : -1),
                row("Parabolic SAR (0.02, 0.20)",
                        String.format(tr, "%.4f (%s)", parabolicSar, psarBullish ? "Pozitif" : "Negatif"),
                        psarBullish ? 1 : -1),
                row("Ichimoku Tenkan / Kijun",
                        String.format(tr, "%.4f / %.4f", ichimokuTenkan, ichimokuKijun),
                        (close > Math.max(ichimokuSpanA, ichimokuSpanB) && ichimokuTenkan > ichimokuKijun) ? 1
                                : ((close < Math.min(ichimokuSpanA, ichimokuSpanB) && ichimokuTenkan < ichimokuKijun) ? -1 : 0)),
                row("ADX(14) (+DI / -DI)",
                        String.format(tr, "%.1f (%.1f / %.1f)", adx14, plusDi14, minusDi14),
                        adx14 < 20.0 ? 0 : (plusDi14 > minusDi14 ? 1 : (minusDi14 > plusDi14 ? -1 : 0))),
                row("RSI(14) & StochRSI",
                        String.format(tr, "%.1f / %.1f", rsi14, stochRsi),
                        (rsi14 > 52.0 && rsi14 < 70.0) || rsi14 < 30.0 ? 1
                                : ((rsi14 < 48.0 && rsi14 > 30.0) || rsi14 >= 70.0 ? -1 : 0)),
                row("MACD / Sinyal / Hist",
                        String.format(tr, "%.4f / %.4f / %.4f", macdLine, macdSignal, macdHistogram),
                        (macdHistogram > 0 && macdLine > macdSignal) ? 1
                                : ((macdHistogram < 0 && macdLine < macdSignal) ? -1 : 0)),
                row("Stoch %K / %D & CCI(20)",
                        String.format(tr, "%.1f / %.1f · CCI %.1f", stochK, stochD, cci20),
                        (stochK > stochD && cci20 > 20.0) ? 1 : ((stochK < stochD && cci20 < -20.0) ? -1 : 0)),
                row("Momentum(10) / AO / UO",
                        String.format(tr, "%.4f / %.4f / %.1f", momentum10, awesomeOscillator, ultimateOscillator),
                        (momentum10 > 0 && awesomeOscillator > 0 && ultimateOscillator >= 50.0) ? 1
                                : ((momentum10 < 0 && awesomeOscillator < 0 && ultimateOscillator < 50.0) ? -1 : 0)),
                row("Bollinger (20,2) & ATR(14)",
                        String.format(tr, "%.4f – %.4f (ATR %.4f)", bbLower, bbUpper, atr14),
                        bbSqueeze ? 0 : (close > bbMiddle ? 1 : (close < bbMiddle ? -1 : 0))),
                row("VWAP & OBV",
                        String.format(tr, "%.4f (OBV %.0f)", vwap, obv),
                        close > vwap * 1.0002 ? 1 : (close < vwap * 0.9998 ? -1 : 0)),
                row("RVOL / CMF(20) / MFI(14)",
                        String.format(tr, "%.2fx / %.2f / %.1f", relativeVolume, cmf20, mfi14),
                        (cmf20 > 0.05 && mfi14 >= 50.0) ? 1 : ((cmf20 < -0.05 && mfi14 < 50.0) ? -1 : 0)),
                row("Volume Profile POC & Pivot",
                        String.format(tr, "POC %.4f / Pivot %.4f", volumeProfilePoc, pivots.pivot()),
                        (close > volumeProfilePoc && close > pivots.pivot()) ? 1
                                : ((close < volumeProfilePoc && close < pivots.pivot()) ? -1 : 0))
        );
    }

    private static IndicatorRowVerdict row(String label, String valueText, int sign) {
        if (sign > 0) {
            return new IndicatorRowVerdict(label, valueText, "AL", "badge-buy");
        }
        if (sign < 0) {
            return new IndicatorRowVerdict(label, valueText, "SAT", "badge-sell");
        }
        return new IndicatorRowVerdict(label, valueText, "TUT", "badge-hold");
    }

    public int buyVerdictCount() {
        return (int) indicatorVerdictRows().stream().filter(r -> "AL".equals(r.action())).count();
    }

    public int sellVerdictCount() {
        return (int) indicatorVerdictRows().stream().filter(r -> "SAT".equals(r.action())).count();
    }

    public int holdVerdictCount() {
        return (int) indicatorVerdictRows().stream().filter(r -> "TUT".equals(r.action())).count();
    }

    public String overallVerdictLabel() {
        int buy = buyVerdictCount();
        int sell = sellVerdictCount();
        if (buy >= sell + 4) return "GÜÇLÜ AL";
        if (buy > sell) return "AL";
        if (sell >= buy + 4) return "GÜÇLÜ SAT";
        if (sell > buy) return "SAT";
        return "TUT (NÖTR)";
    }

    public String overallVerdictBadgeClass() {
        int buy = buyVerdictCount();
        int sell = sellVerdictCount();
        if (buy > sell) return "badge-buy";
        if (sell > buy) return "badge-sell";
        return "badge-hold";
    }
}

