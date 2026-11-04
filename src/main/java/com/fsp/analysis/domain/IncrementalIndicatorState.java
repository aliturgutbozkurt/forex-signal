package com.fsp.analysis.domain;

import com.fsp.analysis.api.DivergenceType;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.analysis.api.PivotLevels;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;

/**
 * Pure Java, O(1) incremental technical analysis state engine with zero heap allocations on {@link #step}.
 * Computes Trend, Momentum, Volatility, Volume, Structure/Pivots, and Regime (SPEC-001 §7.4).
 */
public final class IncrementalIndicatorState {

    private static final int WINDOW = 64; // power of 2 for bitmask indexing
    private static final int MASK = WINDOW - 1;
    private static final int VP_BUCKETS = 16;

    // Ring buffers (primitive arrays, pre-allocated once)
    private final double[] highs = new double[WINDOW];
    private final double[] lows = new double[WINDOW];
    private final double[] closes = new double[WINDOW];
    private final double[] volumes = new double[WINDOW];
    private final double[] midpoints = new double[WINDOW];
    private final double[] rsis = new double[WINDOW];
    private final double[] macds = new double[WINDOW];
    private final double[] bpBuf = new double[WINDOW];
    private final double[] trBuf = new double[WINDOW];
    private final double[] mfPosBuf = new double[WINDOW];
    private final double[] mfNegBuf = new double[WINDOW];
    private final double[] cmfMfvBuf = new double[WINDOW];
    private final double[] vpBucketVol = new double[VP_BUCKETS];

    private int cursor = 0;
    private int count = 0;

    // Trend state
    private double ema9;
    private double ema12;
    private double ema21;
    private double ema26;
    private double ema50;
    private double ema200;
    private double wma8;
    private double wma16;
    private double hma16;
    private double sma20;
    private double ichimokuTenkan;
    private double ichimokuKijun;
    private double ichimokuSpanA;
    private double ichimokuSpanB;

    // Supertrend (10, 3.0)
    private double supertrend;
    private double stUpperBand;
    private double stLowerBand;
    private boolean supertrendBullish = true;

    // Parabolic SAR (0.02, 0.20)
    private double psar;
    private double psarEp;
    private double psarAf = 0.02;
    private boolean psarBullish = true;

    // Wilder ADX(14) & ATR(14) & RSI(14)
    private double atr14;
    private double smoothPlusDm;
    private double smoothMinusDm;
    private double smoothTr14;
    private double plusDi14 = 25.0;
    private double minusDi14 = 25.0;
    private double adx14 = 20.0;
    private double avgGain14;
    private double avgLoss14;
    private double rsi14 = 50.0;

    // Momentum state
    private double stochK = 50.0;
    private double stochD = 50.0;
    private double stochRsi = 50.0;
    private double macdLine;
    private double macdSignal;
    private double macdHistogram;
    private double cci20;
    private double williamsR14 = -50.0;
    private double momentum10;
    private double awesomeOscillator;
    private double ultimateOscillator = 50.0;
    private DivergenceType rsiDivergence = DivergenceType.NONE;
    private DivergenceType macdDivergence = DivergenceType.NONE;

    // Volatility & Volume state
    private double bbUpper;
    private double bbMiddle;
    private double bbLower;
    private double bbWidth;
    private boolean bbSqueeze;
    private double keltnerUpper;
    private double keltnerLower;
    private double donchianUpper;
    private double donchianLower;
    private double relativeVolume = 1.0;
    private double obv;
    private double vwapCumPv;
    private double vwapCumVol;
    private double vwapCumPv2;
    private double vwap;
    private double vwapUpperBand;
    private double vwapLowerBand;
    private double cmf20;
    private double mfi14 = 50.0;
    private double accumulationDistribution;
    private double vpPoc;
    private double vpVah;
    private double vpVal;

    // Structure & Regime
    private double prevClose;
    private double prevHigh;
    private double prevLow;
    private double prevTypical;
    private double swingHigh;
    private double swingLow;

    private final RegimeClassifier regimeClassifier = new RegimeClassifier();

    /**
     * Zero-allocation O(1) hot-path bar update.
     */
    public void step(double open, double high, double low, double close, double volume, boolean newSessionDay) {
        int slot = cursor & MASK;
        highs[slot] = high;
        lows[slot] = low;
        closes[slot] = close;
        volumes[slot] = volume;
        double mid = (high + low) * 0.5;
        midpoints[slot] = mid;
        double typical = (high + low + close) / 3.0;

        if (count == 0) {
            initializeFirstBar(high, low, close, volume, typical);
            cursor++;
            count = 1;
            return;
        }

        int n = Math.min(count + 1, WINDOW);

        // 1. Exponential Moving Averages (O(1))
        ema9 = emaStep(close, ema9, 9);
        ema12 = emaStep(close, ema12, 12);
        ema21 = emaStep(close, ema21, 21);
        ema26 = emaStep(close, ema26, 26);
        ema50 = emaStep(close, ema50, 50);
        ema200 = emaStep(close, ema200, 200);

        // HMA(16) approximation via WMA(8) & WMA(16)
        wma8 = emaStep(close, wma8, 5);
        wma16 = emaStep(close, wma16, 9);
        double rawHma = 2.0 * wma8 - wma16;
        hma16 = emaStep(rawHma, hma16, 4);

        // 2. True Range, ATR(14), +DM/-DM, ADX(14)
        double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        trBuf[slot] = tr;
        bpBuf[slot] = close - Math.min(low, prevClose);

        atr14 = ((atr14 * 13.0) + tr) / 14.0;
        double upMove = high - prevHigh;
        double downMove = prevLow - low;
        double plusDm = (upMove > downMove && upMove > 0) ? upMove : 0.0;
        double minusDm = (downMove > upMove && downMove > 0) ? downMove : 0.0;

        smoothTr14 = smoothTr14 - (smoothTr14 / 14.0) + tr;
        smoothPlusDm = smoothPlusDm - (smoothPlusDm / 14.0) + plusDm;
        smoothMinusDm = smoothMinusDm - (smoothMinusDm / 14.0) + minusDm;

        if (smoothTr14 > 1e-12) {
            plusDi14 = 100.0 * (smoothPlusDm / smoothTr14);
            minusDi14 = 100.0 * (smoothMinusDm / smoothTr14);
        }
        double diSum = plusDi14 + minusDi14;
        double dx = diSum > 1e-12 ? 100.0 * Math.abs(plusDi14 - minusDi14) / diSum : 0.0;
        adx14 = ((adx14 * 13.0) + dx) / 14.0;

        // 3. Wilder RSI(14)
        double change = close - prevClose;
        double gain = change > 0 ? change : 0.0;
        double loss = change < 0 ? -change : 0.0;
        avgGain14 = ((avgGain14 * 13.0) + gain) / 14.0;
        avgLoss14 = ((avgLoss14 * 13.0) + loss) / 14.0;
        if (avgLoss14 <= 1e-12) {
            rsi14 = avgGain14 <= 1e-12 ? 50.0 : 100.0;
        } else {
            double rs = avgGain14 / avgLoss14;
            rsi14 = 100.0 - (100.0 / (1.0 + rs));
        }
        rsis[slot] = rsi14;

        // 4. MACD(12, 26, 9)
        macdLine = ema12 - ema26;
        macdSignal = emaStep(macdLine, macdSignal, 9);
        macdHistogram = macdLine - macdSignal;
        macds[slot] = macdLine;

        // 5. Windowed SMA(20), Bollinger(20, 2.0), CCI(20), Donchian(20), RVOL(20), CMF(20)
        double mfm = (high > low) ? ((close - low) - (high - close)) / (high - low) : 0.0;
        double mfv = mfm * volume;
        cmfMfvBuf[slot] = mfv;
        accumulationDistribution += mfv;

        double rawMf = typical * volume;
        mfPosBuf[slot] = typical >= prevTypical ? rawMf : 0.0;
        mfNegBuf[slot] = typical < prevTypical ? rawMf : 0.0;

        int win20 = Math.min(n, 20);
        double sumClose20 = 0.0;
        double sumVol20 = 0.0;
        double sumMfv20 = 0.0;
        double hi20 = high;
        double lo20 = low;

        for (int i = 0; i < win20; i++) {
            int idx = (cursor - i) & MASK;
            double c = closes[idx];
            double h = highs[idx];
            double l = lows[idx];
            sumClose20 += c;
            sumVol20 += volumes[idx];
            sumMfv20 += cmfMfvBuf[idx];
            if (h > hi20) hi20 = h;
            if (l < lo20) lo20 = l;
        }
        sma20 = sumClose20 / win20;
        bbMiddle = sma20;
        donchianUpper = hi20;
        donchianLower = lo20;
        swingHigh = hi20;
        swingLow = lo20;

        double avgVol20 = sumVol20 / win20;
        relativeVolume = avgVol20 > 1e-9 ? volume / avgVol20 : 1.0;
        cmf20 = sumVol20 > 1e-9 ? sumMfv20 / sumVol20 : 0.0;

        double varianceSum = 0.0;
        double meanDevSum = 0.0;
        for (int i = 0; i < win20; i++) {
            int idx = (cursor - i) & MASK;
            double diff = closes[idx] - sma20;
            varianceSum += diff * diff;
            meanDevSum += Math.abs(diff);
        }
        double stdDev20 = Math.sqrt(varianceSum / win20);
        double meanDev20 = meanDevSum / win20;
        bbUpper = bbMiddle + 2.0 * stdDev20;
        bbLower = bbMiddle - 2.0 * stdDev20;
        bbWidth = bbMiddle > 1e-9 ? (bbUpper - bbLower) / bbMiddle : 0.0;
        cci20 = meanDev20 > 1e-9 ? (typical - sma20) / (0.015 * meanDev20) : 0.0;

        // Keltner Channels (20, 1.5 ATR) & Squeeze
        keltnerUpper = ema21 + 1.5 * atr14;
        keltnerLower = ema21 - 1.5 * atr14;
        bbSqueeze = bbUpper <= keltnerUpper && bbLower >= keltnerLower;

        // 6. 14-bar Window: Stochastic(14,3), StochRSI(14), Williams %R(14), MFI(14)
        int win14 = Math.min(n, 14);
        double hi14 = high;
        double lo14 = low;
        double minRsi14 = rsi14;
        double maxRsi14 = rsi14;
        double posMf14 = 0.0;
        double negMf14 = 0.0;
        for (int i = 0; i < win14; i++) {
            int idx = (cursor - i) & MASK;
            if (highs[idx] > hi14) hi14 = highs[idx];
            if (lows[idx] < lo14) lo14 = lows[idx];
            if (rsis[idx] < minRsi14) minRsi14 = rsis[idx];
            if (rsis[idx] > maxRsi14) maxRsi14 = rsis[idx];
            posMf14 += mfPosBuf[idx];
            negMf14 += mfNegBuf[idx];
        }
        double range14 = hi14 - lo14;
        double rawK = range14 > 1e-12 ? 100.0 * (close - lo14) / range14 : 50.0;
        stochK = emaStep(rawK, stochK, 3);
        stochD = emaStep(stochK, stochD, 3);
        williamsR14 = range14 > 1e-12 ? -100.0 * (hi14 - close) / range14 : -50.0;

        double rsiRange = maxRsi14 - minRsi14;
        stochRsi = rsiRange > 1e-9 ? 100.0 * (rsi14 - minRsi14) / rsiRange : 50.0;
        mfi14 = negMf14 <= 1e-9 ? 100.0 : 100.0 - (100.0 / (1.0 + (posMf14 / negMf14)));

        // 7. Ichimoku (9, 26, 52)
        ichimokuTenkan = midpointRange(Math.min(n, 9));
        ichimokuKijun = midpointRange(Math.min(n, 26));
        ichimokuSpanA = (ichimokuTenkan + ichimokuKijun) * 0.5;
        ichimokuSpanB = midpointRange(Math.min(n, 52));

        // 8. Supertrend (10, 3.0)
        double basicUpper = mid + 3.0 * atr14;
        double basicLower = mid - 3.0 * atr14;
        stUpperBand = (basicUpper < stUpperBand || prevClose > stUpperBand) ? basicUpper : stUpperBand;
        stLowerBand = (basicLower > stLowerBand || prevClose < stLowerBand) ? basicLower : stLowerBand;
        if (supertrendBullish && close < stLowerBand) {
            supertrendBullish = false;
        } else if (!supertrendBullish && close > stUpperBand) {
            supertrendBullish = true;
        }
        supertrend = supertrendBullish ? stLowerBand : stUpperBand;

        // 9. Parabolic SAR (0.02, 0.20)
        updateParabolicSar(high, low);

        // 10. Momentum(10), Awesome Oscillator (5/34), Ultimate Oscillator (7/14/28)
        int lag10Idx = (cursor - Math.min(n - 1, 10)) & MASK;
        momentum10 = close - closes[lag10Idx];
        awesomeOscillator = meanMidpoint(Math.min(n, 5)) - meanMidpoint(Math.min(n, 34));
        ultimateOscillator = computeUltimateOscillator(n);

        // 11. OBV & Session-Aware VWAP (+1σ band)
        if (close > prevClose) {
            obv += volume;
        } else if (close < prevClose) {
            obv -= volume;
        }
        if (newSessionDay || vwapCumVol <= 0.0) {
            vwapCumPv = typical * volume;
            vwapCumVol = volume;
            vwapCumPv2 = typical * typical * volume;
        } else {
            vwapCumPv += typical * volume;
            vwapCumVol += volume;
            vwapCumPv2 += typical * typical * volume;
        }
        vwap = vwapCumVol > 1e-9 ? vwapCumPv / vwapCumVol : close;
        double vwapVar = vwapCumVol > 1e-9 ? Math.max(0.0, (vwapCumPv2 / vwapCumVol) - (vwap * vwap)) : 0.0;
        double vwapStd = Math.sqrt(vwapVar);
        vwapUpperBand = vwap + vwapStd;
        vwapLowerBand = vwap - vwapStd;

        // 12. Volume Profile (POC / VAH / VAL over window)
        computeVolumeProfile(win20, lo20, hi20);

        // 13. RSI & MACD Divergence Detection
        detectDivergences(n, close);

        // 14. Update Regime with Hysteresis
        double atrPct = close > 1e-9 ? (atr14 / close) * 100.0 : 0.0;
        regimeClassifier.classify(adx14, plusDi14, minusDi14, atrPct, bbWidth, ema21, ema50);

        prevClose = close;
        prevHigh = high;
        prevLow = low;
        prevTypical = typical;
        cursor++;
        if (count < WINDOW) {
            count++;
        }
    }

    public IndicatorSnapshotDto toSnapshot(Symbol symbol, Timeframe timeframe, long barIndex, Instant closeTime) {
        double range = Math.max(1e-9, prevHigh - prevLow);
        double p = (prevHigh + prevLow + prevClose) / 3.0;
        PivotLevels pivots = new PivotLevels(
                p,
                2.0 * p - prevLow,
                p + range,
                prevHigh + 2.0 * (p - prevLow),
                2.0 * p - prevHigh,
                p - range,
                prevLow - 2.0 * (prevHigh - p),
                p + 0.382 * range,
                p + 0.618 * range,
                p - 0.382 * range,
                p - 0.618 * range,
                prevClose + range * 1.1 / 4.0,
                prevClose + range * 1.1 / 2.0,
                prevClose - range * 1.1 / 4.0,
                prevClose - range * 1.1 / 2.0,
                swingHigh,
                swingLow
        );
        double atrPct = prevClose > 1e-9 ? (atr14 / prevClose) * 100.0 : 0.0;
        return new IndicatorSnapshotDto(
                symbol,
                timeframe,
                barIndex,
                closeTime,
                prevClose,
                ema9,
                ema21,
                ema50,
                ema200,
                sma20,
                hma16,
                ichimokuTenkan,
                ichimokuKijun,
                ichimokuSpanA,
                ichimokuSpanB,
                supertrend,
                supertrendBullish,
                psar,
                psarBullish,
                adx14,
                plusDi14,
                minusDi14,
                rsi14,
                stochK,
                stochD,
                stochRsi,
                macdLine,
                macdSignal,
                macdHistogram,
                cci20,
                williamsR14,
                momentum10,
                awesomeOscillator,
                ultimateOscillator,
                rsiDivergence,
                macdDivergence,
                atr14,
                atrPct,
                bbUpper,
                bbMiddle,
                bbLower,
                bbWidth,
                bbSqueeze,
                keltnerUpper,
                keltnerLower,
                donchianUpper,
                donchianLower,
                relativeVolume,
                obv,
                vwap,
                vwapUpperBand,
                vwapLowerBand,
                cmf20,
                mfi14,
                accumulationDistribution,
                vpPoc,
                vpVah,
                vpVal,
                pivots,
                regimeClassifier.currentRegime()
        );
    }

    public MarketRegime currentRegime() {
        return regimeClassifier.currentRegime();
    }

    private void initializeFirstBar(double high, double low, double close, double volume, double typical) {
        ema9 = close;
        ema12 = close;
        ema21 = close;
        ema26 = close;
        ema50 = close;
        ema200 = close;
        wma8 = close;
        wma16 = close;
        hma16 = close;
        sma20 = close;
        ichimokuTenkan = (high + low) * 0.5;
        ichimokuKijun = ichimokuTenkan;
        ichimokuSpanA = ichimokuTenkan;
        ichimokuSpanB = ichimokuTenkan;
        atr14 = Math.max(high - low, close * 0.002);
        smoothTr14 = atr14 * 14.0;
        stUpperBand = close + 3.0 * atr14;
        stLowerBand = close - 3.0 * atr14;
        supertrend = stLowerBand;
        psar = low;
        psarEp = high;
        bbMiddle = close;
        bbUpper = close + 2.0 * atr14;
        bbLower = close - 2.0 * atr14;
        keltnerUpper = close + 1.5 * atr14;
        keltnerLower = close - 1.5 * atr14;
        donchianUpper = high;
        donchianLower = low;
        swingHigh = high;
        swingLow = low;
        vwapCumPv = typical * volume;
        vwapCumVol = volume;
        vwapCumPv2 = typical * typical * volume;
        vwap = typical;
        vwapUpperBand = typical + atr14 * 0.5;
        vwapLowerBand = typical - atr14 * 0.5;
        vpPoc = close;
        vpVah = high;
        vpVal = low;
        prevClose = close;
        prevHigh = high;
        prevLow = low;
        prevTypical = typical;
        rsis[0] = 50.0;
        trBuf[0] = atr14;
        bpBuf[0] = close - low;
    }

    private static double emaStep(double value, double prevEma, int period) {
        double alpha = 2.0 / (period + 1.0);
        return (value - prevEma) * alpha + prevEma;
    }

    private double midpointRange(int len) {
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        for (int i = 0; i < len; i++) {
            int idx = (cursor - i) & MASK;
            if (highs[idx] > hi) hi = highs[idx];
            if (lows[idx] < lo) lo = lows[idx];
        }
        return (hi + lo) * 0.5;
    }

    private double meanMidpoint(int len) {
        double sum = 0.0;
        for (int i = 0; i < len; i++) {
            sum += midpoints[(cursor - i) & MASK];
        }
        return sum / len;
    }

    private void updateParabolicSar(double high, double low) {
        psar = psar + psarAf * (psarEp - psar);
        if (psarBullish) {
            if (low < psar) {
                psarBullish = false;
                psar = psarEp;
                psarEp = low;
                psarAf = 0.02;
            } else if (high > psarEp) {
                psarEp = high;
                psarAf = Math.min(0.20, psarAf + 0.02);
            }
        } else {
            if (high > psar) {
                psarBullish = true;
                psar = psarEp;
                psarEp = high;
                psarAf = 0.02;
            } else if (low < psarEp) {
                psarEp = low;
                psarAf = Math.min(0.20, psarAf + 0.02);
            }
        }
    }

    private double computeUltimateOscillator(int n) {
        int l7 = Math.min(n, 7);
        int l14 = Math.min(n, 14);
        int l28 = Math.min(n, 28);
        double bp7 = 0.0, tr7 = 0.0, bp14 = 0.0, tr14 = 0.0, bp28 = 0.0, tr28 = 0.0;
        for (int i = 0; i < l28; i++) {
            int idx = (cursor - i) & MASK;
            double b = bpBuf[idx];
            double t = trBuf[idx];
            if (i < l7) {
                bp7 += b;
                tr7 += t;
            }
            if (i < l14) {
                bp14 += b;
                tr14 += t;
            }
            bp28 += b;
            tr28 += t;
        }
        double avg7 = tr7 > 1e-12 ? bp7 / tr7 : 0.5;
        double avg14 = tr14 > 1e-12 ? bp14 / tr14 : 0.5;
        double avg28 = tr28 > 1e-12 ? bp28 / tr28 : 0.5;
        return 100.0 * ((4.0 * avg7) + (2.0 * avg14) + avg28) / 7.0;
    }

    private void computeVolumeProfile(int len, double minLow, double maxHigh) {
        double span = maxHigh - minLow;
        if (span <= 1e-12) {
            vpPoc = prevClose;
            vpVah = maxHigh;
            vpVal = minLow;
            return;
        }
        for (int b = 0; b < VP_BUCKETS; b++) {
            vpBucketVol[b] = 0.0;
        }
        double step = span / VP_BUCKETS;
        for (int i = 0; i < len; i++) {
            int idx = (cursor - i) & MASK;
            int bucket = (int) ((closes[idx] - minLow) / step);
            if (bucket < 0) bucket = 0;
            if (bucket >= VP_BUCKETS) bucket = VP_BUCKETS - 1;
            vpBucketVol[bucket] += volumes[idx];
        }
        int pocBucket = 0;
        for (int b = 1; b < VP_BUCKETS; b++) {
            if (vpBucketVol[b] > vpBucketVol[pocBucket]) {
                pocBucket = b;
            }
        }
        vpPoc = minLow + (pocBucket + 0.5) * step;
        vpVal = minLow + Math.max(0, pocBucket - 3) * step;
        vpVah = minLow + Math.min(VP_BUCKETS, pocBucket + 4) * step;
    }

    private void detectDivergences(int n, double close) {
        if (n < 6) {
            rsiDivergence = DivergenceType.NONE;
            macdDivergence = DivergenceType.NONE;
            return;
        }
        int prevIdx = (cursor - 5) & MASK;
        double oldClose = closes[prevIdx];
        double oldRsi = rsis[prevIdx];
        double oldMacd = macds[prevIdx];

        if (close < oldClose && rsi14 > oldRsi + 2.0) {
            rsiDivergence = DivergenceType.BULLISH;
        } else if (close > oldClose && rsi14 < oldRsi - 2.0) {
            rsiDivergence = DivergenceType.BEARISH;
        } else {
            rsiDivergence = DivergenceType.NONE;
        }

        if (close < oldClose && macdLine > oldMacd) {
            macdDivergence = DivergenceType.BULLISH;
        } else if (close > oldClose && macdLine < oldMacd) {
            macdDivergence = DivergenceType.BEARISH;
        } else {
            macdDivergence = DivergenceType.NONE;
        }
    }
}

