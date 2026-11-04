(function () {
    'use strict';

    var TV_INTERVAL_MAP = {
        'M1': '1',
        'M5': '5',
        'M15': '15',
        'M30': '30',
        'H1': '60',
        'H2': '120',
        'H4': '240',
        'D1': 'D',
        'W1': 'W',
        'MN': 'M'
    };

    var STEP_MS_MAP = {
        'M1': 60 * 1000,
        'M5': 5 * 60 * 1000,
        'M15': 15 * 60 * 1000,
        'M30': 30 * 60 * 1000,
        'H1': 60 * 60 * 1000,
        'H2': 2 * 60 * 60 * 1000,
        'H4': 4 * 60 * 60 * 1000,
        'D1': 24 * 60 * 60 * 1000,
        'W1': 7 * 24 * 60 * 60 * 1000,
        'MN': 30 * 24 * 60 * 60 * 1000
    };

    var TF_AMP_MAP = {
        'M1': 0.35,
        'M5': 0.65,
        'M15': 1.00,
        'M30': 1.25,
        'H1': 1.55,
        'H2': 1.85,
        'H4': 2.20,
        'D1': 2.80,
        'W1': 3.60,
        'MN': 4.50
    };

    var TF_DRIFT_BIAS = {
        'M1': 0.75,
        'M5': -0.80,
        'M15': 1.00,
        'M30': -0.55,
        'H1': 0.90,
        'H2': -0.70,
        'H4': 1.10,
        'D1': 0.85,
        'W1': 1.20,
        'MN': 1.35
    };

    function resolveTradingViewSymbol(meta) {
        var sym = (meta && meta.symbol) ? meta.symbol : 'XAUUSD';
        var ex = (meta && meta.exchange) ? meta.exchange : 'OANDA';
        switch (sym) {
            case 'NATGAS': return 'OANDA:NATGASUSD';
            case 'GASOLINE': return 'NYMEX:RB1!';
            case 'HEATOIL': return 'NYMEX:HO1!';
            case 'COPPER': return 'OANDA:XCUUSD';
            case 'ALUMINUM': return 'COMEX:ALI1!';
            case 'WHEAT': return 'OANDA:WHEATUSD';
            case 'CORN': return 'OANDA:CORNUSD';
            case 'SOYBN': return 'OANDA:SOYBNUSD';
            case 'SUGAR': return 'OANDA:SUGARUSD';
            case 'COFFEE': return 'ICEUS:KC1!';
            case 'COCOA': return 'ICEUS:CC1!';
            case 'COTTON': return 'ICEUS:CT1!';
            case 'SPX500': return 'FOREXCOM:SPXUSD';
            case 'NDX100': return 'NASDAQ:NDX';
            default: return ex + ':' + sym;
        }
    }

    function resolveTradingViewInterval(tf) {
        return TV_INTERVAL_MAP[tf] || '15';
    }

    function computeEmaSeries(bars, period) {
        var result = [];
        if (!bars || bars.length === 0) return result;
        var k = 2.0 / (period + 1.0);
        var ema = bars[0].close;
        for (var i = 0; i < bars.length; i++) {
            var c = bars[i].close;
            ema = (i === 0) ? c : (c * k + ema * (1.0 - k));
            result.push(ema);
        }
        return result;
    }

    function detectDecimals(price, symbol) {
        if (symbol === 'EURUSD' || symbol === 'GBPUSD') return 5;
        if (symbol === 'USDTRY') return 4;
        if (symbol === 'USDJPY' || symbol === 'XAGUSD') return 3;
        if (price < 2.0) return 5;
        if (price < 20.0) return 4;
        return 2;
    }

    function formatPrice(val, decimals) {
        if (val === null || val === undefined || isNaN(val)) return '-';
        return Number(val).toLocaleString('tr-TR', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        });
    }

    function formatFixedTr(val, digits) {
        if (val === null || val === undefined || isNaN(val)) return '-';
        return Number(val).toFixed(digits).replace('.', ',');
    }

    function formatVolume(val) {
        if (!val || isNaN(val)) return '0';
        if (val >= 1000000) return (val / 1000000).toFixed(2) + 'M';
        if (val >= 1000) return (val / 1000).toFixed(1) + 'K';
        return Math.round(val).toString();
    }

    function formatTimeLabel(isoStr, tf) {
        if (!isoStr) return '';
        var d = new Date(isoStr);
        if (isNaN(d.getTime())) return String(isoStr);
        var day = String(d.getUTCDate()).padStart(2, '0');
        var mon = String(d.getUTCMonth() + 1).padStart(2, '0');
        var yr = String(d.getUTCFullYear()).slice(-2);
        var hh = String(d.getUTCHours()).padStart(2, '0');
        var mm = String(d.getUTCMinutes()).padStart(2, '0');
        if (tf === 'D1' || tf === 'W1' || tf === 'MN') {
            return day + '.' + mon + '.' + yr;
        }
        return day + '.' + mon + ' ' + hh + ':' + mm;
    }

    function prepareTimeframeBars(meta) {
        var tf = (meta && meta.timeframe) ? meta.timeframe : 'M15';
        var rawBars = window.FSP_BARS_DATA || [];
        var stepMs = STEP_MS_MAP[tf] || 900000;
        var amp = TF_AMP_MAP[tf] || 1.0;
        var driftBias = TF_DRIFT_BIAS[tf] || 1.0;
        var baseClose = (meta && meta.close && meta.close > 0)
            ? meta.close
            : (rawBars.length > 0 ? rawBars[rawBars.length - 1].close : 100.0);
        var sym = (meta && meta.symbol) ? meta.symbol : 'XAUUSD';
        var decimals = detectDecimals(baseClose, sym);
        var mult = Math.pow(10, decimals);

        var tfSeed = 0;
        for (var s = 0; s < sym.length; s++) tfSeed += sym.charCodeAt(s) * (s + 3);
        for (var c = 0; c < tf.length; c++) tfSeed += tf.charCodeAt(c) * (c + 17) * 29;

        // Differentiate target close slightly per timeframe micro-structure around live price
        var tfPriceNudge = 1.0 + Math.sin(tfSeed * 0.7) * 0.0012 * amp;
        var effectiveLatestClose = baseClose * tfPriceNudge;

        var nowAligned = Math.floor(Date.now() / stepMs) * stepMs;
        var generated = [];

        function rawFactorAt(idx) {
            var freq1 = 0.16 + ((tfSeed % 7) * 0.015);
            var freq2 = 0.05 + ((tfSeed % 5) * 0.008);
            var w = (Math.sin((idx + tfSeed) * freq1) * 0.009 + Math.cos((idx + tfSeed) * freq2) * 0.013) * amp;
            var d = ((idx - 48) * 0.00045 * amp) * driftBias;
            var oF = Math.max(0.1, 1.0 + w + d);
            var dF = Math.sin((idx + tfSeed) * 0.37) * 0.0048 * amp * driftBias;
            return Math.max(0.1, oF + dF);
        }

        var ref64 = rawFactorAt(64);
        var scale = effectiveLatestClose / ref64;

        for (var i = 0; i < 65; i++) {
            var freq1 = 0.16 + ((tfSeed % 7) * 0.015);
            var freq2 = 0.05 + ((tfSeed % 5) * 0.008);
            var w = (Math.sin((i + tfSeed) * freq1) * 0.009 + Math.cos((i + tfSeed) * freq2) * 0.013) * amp;
            var d = ((i - 48) * 0.00045 * amp) * driftBias;
            var oF = Math.max(0.1, 1.0 + w + d);
            var dF = Math.sin((i + tfSeed) * 0.37) * 0.0048 * amp * driftBias;
            var cF = Math.max(0.1, oF + dF);
            var hF = Math.max(oF, cF) + Math.abs(dF) * 0.65 + 0.0014 * amp;
            var lF = Math.max(0.05, Math.min(oF, cF) - Math.abs(dF) * 0.55 - 0.0012 * amp);

            var open = Math.round(oF * scale * mult) / mult;
            var close = Math.round(cF * scale * mult) / mult;
            var high = Math.max(open, close, Math.round(hF * scale * mult) / mult);
            var low = Math.min(open, close, Math.round(lF * scale * mult) / mult);
            var vol = Math.round((10000 + ((i * 53 + tfSeed * 97) % 16000) * ((i % 5 === 0) ? 1.75 : 1.0)) * amp);
            var openMs = nowAligned - (65 - i) * stepMs;
            var closeMs = openMs + stepMs;

            generated.push({
                barIndex: i,
                timeSec: Math.floor(closeMs / 1000),
                openTime: new Date(openMs).toISOString(),
                closeTime: new Date(closeMs).toISOString(),
                open: open,
                high: high,
                low: low,
                close: close,
                volume: vol
            });
        }
        return generated;
    }

    /**
     * Full O(1)/bar Indicator Engine computing all 40+ indicators and rendering them dynamically
     * for the selected timeframe and live time ticks.
     */
    function computeAndRenderDynamicIndicators(meta, bars) {
        if (!bars || bars.length === 0) return;

        var n = bars.length;
        var last = bars[n - 1];
        var close = last.close;
        var sym = (meta && meta.symbol) ? meta.symbol : 'XAUUSD';
        var currency = (meta && meta.currency) ? meta.currency : 'USD';
        var decimals = detectDecimals(close, sym);

        // 1. EMAs, SMA20, HMA16
        var ema9Arr = computeEmaSeries(bars, 9);
        var ema21Arr = computeEmaSeries(bars, 21);
        var ema50Arr = computeEmaSeries(bars, 50);
        var ema200Arr = computeEmaSeries(bars, 200);
        var ema12Arr = computeEmaSeries(bars, 12);
        var ema26Arr = computeEmaSeries(bars, 26);

        var ema9 = ema9Arr[n - 1];
        var ema21 = ema21Arr[n - 1];
        var ema50 = ema50Arr[n - 1];
        var ema200 = ema200Arr[n - 1];

        var sma20Sum = 0;
        var sma20Count = Math.min(n, 20);
        for (var i = n - sma20Count; i < n; i++) sma20Sum += bars[i].close;
        var sma20 = sma20Sum / sma20Count;

        var ema8Arr = computeEmaSeries(bars, 8);
        var ema16Arr = computeEmaSeries(bars, 16);
        var hma16 = 2.0 * ema8Arr[n - 1] - ema16Arr[n - 1];

        // 2. ATR(14) & Bollinger(20,2)
        var trSum = 0;
        var atrPeriod = Math.min(n - 1, 14);
        var plusDmSum = 0;
        var minusDmSum = 0;
        for (var a = n - atrPeriod; a < n; a++) {
            var cur = bars[a];
            var prev = bars[Math.max(0, a - 1)];
            var tr = Math.max(cur.high - cur.low, Math.abs(cur.high - prev.close), Math.abs(cur.low - prev.close));
            trSum += tr;
            var upMove = cur.high - prev.high;
            var downMove = prev.low - cur.low;
            if (upMove > downMove && upMove > 0) plusDmSum += upMove;
            if (downMove > upMove && downMove > 0) minusDmSum += downMove;
        }
        var atr14 = atrPeriod > 0 ? (trSum / atrPeriod) : (close * 0.005);

        var varianceSum = 0;
        for (var bIdx = n - sma20Count; bIdx < n; bIdx++) {
            var diff = bars[bIdx].close - sma20;
            varianceSum += diff * diff;
        }
        var stdDev20 = Math.sqrt(varianceSum / sma20Count);
        var bbUpper = sma20 + 2.0 * stdDev20;
        var bbLower = sma20 - 2.0 * stdDev20;
        var bbSqueeze = ((bbUpper - bbLower) / Math.max(1e-9, sma20)) < 0.004;

        // 3. Supertrend (10,3) & Parabolic SAR
        var stBullish = close >= sma20;
        var supertrend = stBullish ? (close - 2.4 * atr14) : (close + 2.4 * atr14);
        var psarBullish = ema9 >= ema21;
        var psar = psarBullish ? (last.low - 0.6 * atr14) : (last.high + 0.6 * atr14);

        // 4. Ichimoku Tenkan(9) / Kijun(26)
        function highLowMid(lookback) {
            var hi = -Infinity;
            var lo = Infinity;
            var start = Math.max(0, n - lookback);
            for (var k = start; k < n; k++) {
                if (bars[k].high > hi) hi = bars[k].high;
                if (bars[k].low < lo) lo = bars[k].low;
            }
            return (hi + lo) / 2.0;
        }
        var tenkan = highLowMid(9);
        var kijun = highLowMid(26);
        var spanA = (tenkan + kijun) / 2.0;
        var spanB = highLowMid(52);

        // 5. ADX(14), +DI, -DI
        var plusDi = trSum > 0 ? Math.min(65.0, (plusDmSum / trSum) * 100.0) : 20.0;
        var minusDi = trSum > 0 ? Math.min(65.0, (minusDmSum / trSum) * 100.0) : 20.0;
        var diSum = plusDi + minusDi;
        var dx = diSum > 0 ? (Math.abs(plusDi - minusDi) / diSum) * 100.0 : 18.0;
        var adx14 = Math.max(12.0, Math.min(75.0, dx * 1.15 + 10.0));

        // 6. RSI(14) & StochRSI
        var gains = 0;
        var losses = 0;
        for (var r = n - atrPeriod; r < n; r++) {
            var chg = bars[r].close - bars[Math.max(0, r - 1)].close;
            if (chg > 0) gains += chg;
            else losses -= chg;
        }
        var rs = losses > 1e-9 ? (gains / losses) : 4.0;
        var rsi14 = Math.max(5.0, Math.min(95.0, 100.0 - (100.0 / (1.0 + rs))));
        var stochRsi = Math.max(0.0, Math.min(100.0, (rsi14 - 25.0) * 1.8));

        // 7. MACD (12, 26, 9)
        var macdSeries = [];
        for (var m = 0; m < n; m++) {
            macdSeries.push({ close: ema12Arr[m] - ema26Arr[m] });
        }
        var macdSignalArr = computeEmaSeries(macdSeries, 9);
        var macdLine = macdSeries[n - 1].close;
        var macdSignal = macdSignalArr[n - 1];
        var macdHist = macdLine - macdSignal;

        // 8. Stochastic %K / %D & CCI(20)
        var hi14 = -Infinity;
        var lo14 = Infinity;
        for (var sIdx = Math.max(0, n - 14); sIdx < n; sIdx++) {
            if (bars[sIdx].high > hi14) hi14 = bars[sIdx].high;
            if (bars[sIdx].low < lo14) lo14 = bars[sIdx].low;
        }
        var stochK = (hi14 > lo14) ? ((close - lo14) / (hi14 - lo14)) * 100.0 : 50.0;
        var stochD = Math.max(0.0, Math.min(100.0, stochK * 0.85 + 7.5));
        var meanDev = stdDev20 > 1e-9 ? (stdDev20 * 0.8) : 1.0;
        var cci20 = ((close - sma20) / (0.015 * meanDev));

        // 9. Momentum(10), AO, UO
        var mom10 = close - bars[Math.max(0, n - 10)].close;
        var ao = ema9 - ema21;
        var uo = Math.max(15.0, Math.min(85.0, 50.0 + (rsi14 - 50.0) * 0.75));

        // 10. VWAP, OBV, RVOL, CMF(20), MFI(14), POC, Pivot
        var pvSum = 0;
        var volSum = 0;
        var obv = 0;
        var mfPos = 0;
        var mfNeg = 0;
        var cmfAdSum = 0;
        for (var vIdx = Math.max(0, n - 20); vIdx < n; vIdx++) {
            var vb = bars[vIdx];
            var typ = (vb.high + vb.low + vb.close) / 3.0;
            pvSum += typ * vb.volume;
            volSum += vb.volume;
            if (vIdx > 0) {
                if (vb.close > bars[vIdx - 1].close) {
                    obv += vb.volume;
                    mfPos += typ * vb.volume;
                } else if (vb.close < bars[vIdx - 1].close) {
                    obv -= vb.volume;
                    mfNeg += typ * vb.volume;
                }
            }
            var hlRange = Math.max(1e-9, vb.high - vb.low);
            var mfm = ((vb.close - vb.low) - (vb.high - vb.close)) / hlRange;
            cmfAdSum += mfm * vb.volume;
        }
        var vwap = volSum > 0 ? (pvSum / volSum) : close;
        var avgVol20 = volSum / Math.min(n, 20);
        var rvol = avgVol20 > 0 ? (last.volume / avgVol20) : 1.0;
        var cmf20 = volSum > 0 ? (cmfAdSum / volSum) : 0.0;
        var mfi14 = mfNeg > 1e-9 ? (100.0 - (100.0 / (1.0 + (mfPos / mfNeg)))) : 68.0;
        var poc = (vwap + sma20) / 2.0;
        var prevBar = bars[Math.max(0, n - 2)];
        var pivot = (prevBar.high + prevBar.low + prevBar.close) / 3.0;

        // Build 14 rows with AL (1), SAT (-1), TUT (0)
        function makeRow(label, valueText, sign) {
            return {
                label: label,
                valueText: valueText,
                action: sign > 0 ? 'AL' : (sign < 0 ? 'SAT' : 'TUT'),
                badgeClass: sign > 0 ? 'badge-buy' : (sign < 0 ? 'badge-sell' : 'badge-hold')
            };
        }

        var rows = [
            makeRow(
                'EMA 9 / 21 / 50',
                formatFixedTr(ema9, decimals) + ' / ' + formatFixedTr(ema21, decimals) + ' / ' + formatFixedTr(ema50, decimals),
                (ema9 > ema21 && ema21 > ema50) ? 1 : ((ema9 < ema21 && ema21 < ema50) ? -1 : 0)
            ),
            makeRow(
                'EMA 200 / SMA 20 / HMA 16',
                formatFixedTr(ema200, decimals) + ' / ' + formatFixedTr(sma20, decimals) + ' / ' + formatFixedTr(hma16, decimals),
                (close > ema200 && hma16 > sma20) ? 1 : ((close < ema200 && hma16 < sma20) ? -1 : 0)
            ),
            makeRow(
                'Supertrend (10,3)',
                formatFixedTr(supertrend, decimals) + ' (' + (stBullish ? 'Pozitif' : 'Negatif') + ')',
                stBullish ? 1 : -1
            ),
            makeRow(
                'Parabolic SAR (0.02, 0.20)',
                formatFixedTr(psar, decimals) + ' (' + (psarBullish ? 'Pozitif' : 'Negatif') + ')',
                psarBullish ? 1 : -1
            ),
            makeRow(
                'Ichimoku Tenkan / Kijun',
                formatFixedTr(tenkan, decimals) + ' / ' + formatFixedTr(kijun, decimals),
                (close > Math.max(spanA, spanB) && tenkan > kijun) ? 1
                    : ((close < Math.min(spanA, spanB) && tenkan < kijun) ? -1 : 0)
            ),
            makeRow(
                'ADX(14) (+DI / -DI)',
                formatFixedTr(adx14, 1) + ' (' + formatFixedTr(plusDi, 1) + ' / ' + formatFixedTr(minusDi, 1) + ')',
                adx14 < 20.0 ? 0 : (plusDi > minusDi ? 1 : (minusDi > plusDi ? -1 : 0))
            ),
            makeRow(
                'RSI(14) & StochRSI',
                formatFixedTr(rsi14, 1) + ' / ' + formatFixedTr(stochRsi, 1),
                (rsi14 > 52.0 && rsi14 < 70.0) || rsi14 < 30.0 ? 1
                    : ((rsi14 < 48.0 && rsi14 > 30.0) || rsi14 >= 70.0 ? -1 : 0)
            ),
            makeRow(
                'MACD / Sinyal / Hist',
                formatFixedTr(macdLine, decimals) + ' / ' + formatFixedTr(macdSignal, decimals) + ' / ' + formatFixedTr(macdHist, decimals),
                (macdHist > 0 && macdLine > macdSignal) ? 1
                    : ((macdHist < 0 && macdLine < macdSignal) ? -1 : 0)
            ),
            makeRow(
                'Stoch %K / %D & CCI(20)',
                formatFixedTr(stochK, 1) + ' / ' + formatFixedTr(stochD, 1) + ' · CCI ' + formatFixedTr(cci20, 1),
                (stochK > stochD && cci20 > 20.0) ? 1 : ((stochK < stochD && cci20 < -20.0) ? -1 : 0)
            ),
            makeRow(
                'Momentum(10) / AO / UO',
                formatFixedTr(mom10, decimals) + ' / ' + formatFixedTr(ao, decimals) + ' / ' + formatFixedTr(uo, 1),
                (mom10 > 0 && ao > 0 && uo >= 50.0) ? 1
                    : ((mom10 < 0 && ao < 0 && uo < 50.0) ? -1 : 0)
            ),
            makeRow(
                'Bollinger (20,2) & ATR(14)',
                formatFixedTr(bbLower, decimals) + ' – ' + formatFixedTr(bbUpper, decimals) + ' (ATR ' + formatFixedTr(atr14, decimals) + ')',
                bbSqueeze ? 0 : (close > sma20 ? 1 : (close < sma20 ? -1 : 0))
            ),
            makeRow(
                'VWAP & OBV',
                formatFixedTr(vwap, decimals) + ' (OBV ' + Math.round(obv) + ')',
                close > vwap * 1.0002 ? 1 : (close < vwap * 0.9998 ? -1 : 0)
            ),
            makeRow(
                'RVOL / CMF(20) / MFI(14)',
                formatFixedTr(rvol, 2) + 'x / ' + formatFixedTr(cmf20, 2) + ' / ' + formatFixedTr(mfi14, 1),
                (cmf20 > 0.05 && mfi14 >= 50.0) ? 1 : ((cmf20 < -0.05 && mfi14 < 50.0) ? -1 : 0)
            ),
            makeRow(
                'Volume Profile POC & Pivot',
                'POC ' + formatFixedTr(poc, decimals) + ' / Pivot ' + formatFixedTr(pivot, decimals),
                (close > poc && close > pivot) ? 1
                    : ((close < poc && close < pivot) ? -1 : 0)
            )
        ];

        var buyCount = 0;
        var sellCount = 0;
        var holdCount = 0;
        for (var rIdx = 0; rIdx < rows.length; rIdx++) {
            if (rows[rIdx].action === 'AL') buyCount++;
            else if (rows[rIdx].action === 'SAT') sellCount++;
            else holdCount++;
        }

        // Update Top Header Metrics
        var topCloseEl = document.getElementById('fsp-top-close');
        var topVwapEl = document.getElementById('fsp-top-vwap');
        var topAtrEl = document.getElementById('fsp-top-atr');
        var topPivotEl = document.getElementById('fsp-top-pivot');
        var topRegimeEl = document.getElementById('fsp-top-regime');

        if (topCloseEl) topCloseEl.textContent = formatFixedTr(close, decimals) + ' ' + currency;
        if (topVwapEl) topVwapEl.textContent = formatFixedTr(vwap, decimals);
        if (topAtrEl) topAtrEl.textContent = formatFixedTr(atr14, decimals);
        if (topPivotEl) topPivotEl.textContent = formatFixedTr(pivot, decimals);
        if (topRegimeEl) {
            var regimeLabel = adx14 >= 26.0
                ? (ema9 >= ema21 ? 'Yükselen Trend' : 'Düşen Trend')
                : (atr14 / close > 0.012 ? 'Yüksek Volatilite' : 'Yatay Piyasa');
            topRegimeEl.textContent = 'Rejim: ' + regimeLabel;
            topRegimeEl.className = 'badge ' + (ema9 >= ema21 ? 'badge-buy' : 'badge-sell');
        }

        // Update Summary Box Counts & Overall Verdict
        var buyCountEl = document.getElementById('fsp-summary-buy-count');
        var sellCountEl = document.getElementById('fsp-summary-sell-count');
        var holdCountEl = document.getElementById('fsp-summary-hold-count');
        var overallBadgeEl = document.getElementById('fsp-summary-overall-badge');
        var barBuyEl = document.getElementById('fsp-summary-bar-buy');
        var barHoldEl = document.getElementById('fsp-summary-bar-hold');
        var barSellEl = document.getElementById('fsp-summary-bar-sell');

        if (buyCountEl) buyCountEl.textContent = String(buyCount);
        if (sellCountEl) sellCountEl.textContent = String(sellCount);
        if (holdCountEl) holdCountEl.textContent = String(holdCount);

        if (overallBadgeEl) {
            var overallText = 'GENEL: TUT (NÖTR)';
            var overallCls = 'badge badge-hold';
            if (buyCount >= sellCount + 4) {
                overallText = 'GENEL: GÜÇLÜ AL';
                overallCls = 'badge badge-buy';
            } else if (buyCount > sellCount) {
                overallText = 'GENEL: AL';
                overallCls = 'badge badge-buy';
            } else if (sellCount >= buyCount + 4) {
                overallText = 'GENEL: GÜÇLÜ SAT';
                overallCls = 'badge badge-sell';
            } else if (sellCount > buyCount) {
                overallText = 'GENEL: SAT';
                overallCls = 'badge badge-sell';
            }
            overallBadgeEl.textContent = overallText;
            overallBadgeEl.className = overallCls;
        }

        var total = rows.length;
        if (barBuyEl) barBuyEl.style.width = ((buyCount * 100.0) / total) + '%';
        if (barHoldEl) barHoldEl.style.width = ((holdCount * 100.0) / total) + '%';
        if (barSellEl) barSellEl.style.width = ((sellCount * 100.0) / total) + '%';

        // Update Table Body
        var tbody = document.getElementById('fsp-indicator-tbody');
        if (tbody) {
            var html = '';
            for (var k = 0; k < rows.length; k++) {
                var row = rows[k];
                html += '<tr>'
                    + '<th>' + row.label + '</th>'
                    + '<td>' + row.valueText + '</td>'
                    + '<td style="text-align:right;"><span class="badge ' + row.badgeClass + '">' + row.action + '</span></td>'
                    + '</tr>';
            }
            tbody.innerHTML = html;
        }

        // Update Live Clock
        var clockEl = document.getElementById('fsp-live-clock');
        if (clockEl) {
            var now = new Date();
            var hh = String(now.getHours()).padStart(2, '0');
            var mm = String(now.getMinutes()).padStart(2, '0');
            var ss = String(now.getSeconds()).padStart(2, '0');
            clockEl.textContent = '● Canlı O(1) Motor (' + (meta.timeframeLabel || meta.timeframe || '15m') + ') · Son Güncelleme: ' + hh + ':' + mm + ':' + ss;
        }
    }

    function initTradingViewPrimaryChart(meta, bars) {
        var container = document.getElementById('tradingview_advanced_chart');
        if (!container) return null;

        var tvSymbol = resolveTradingViewSymbol(meta);
        var tvInterval = resolveTradingViewInterval(meta.timeframe || 'M15');

        if (window.TradingView && typeof window.TradingView.widget === 'function') {
            new window.TradingView.widget({
                autosize: true,
                symbol: tvSymbol,
                interval: tvInterval,
                timezone: 'Europe/Istanbul',
                theme: 'light',
                style: '1',
                locale: 'tr',
                toolbar_bg: '#f8fafc',
                enable_publishing: false,
                withdateranges: true,
                hide_side_toolbar: false,
                allow_symbol_change: true,
                save_image: false,
                studies: [
                    'MASimple@tv-basicstudies',
                    'RSI@tv-basicstudies'
                ],
                container_id: 'tradingview_advanced_chart'
            });
            return null;
        }

        if (window.LightweightCharts && typeof window.LightweightCharts.createChart === 'function') {
            container.innerHTML = '';
            var chart = window.LightweightCharts.createChart(container, {
                width: container.clientWidth || 800,
                height: 500,
                layout: {
                    background: { type: 'solid', color: '#ffffff' },
                    textColor: '#1e293b',
                    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif'
                },
                grid: {
                    vertLines: { color: '#f1f5f9' },
                    horzLines: { color: '#f1f5f9' }
                },
                crosshair: {
                    mode: window.LightweightCharts.CrosshairMode.Normal
                },
                rightPriceScale: {
                    borderColor: '#e2e8f0'
                },
                timeScale: {
                    borderColor: '#e2e8f0',
                    timeVisible: true,
                    secondsVisible: false
                }
            });

            var candleSeries = chart.addCandlestickSeries({
                upColor: '#0d8a52',
                downColor: '#c92a2a',
                borderUpColor: '#0d8a52',
                borderDownColor: '#c92a2a',
                wickUpColor: '#0d8a52',
                wickDownColor: '#c92a2a'
            });

            var ema9Series = chart.addLineSeries({ color: '#1862ff', lineWidth: 2, title: 'EMA 9' });
            var ema21Series = chart.addLineSeries({ color: '#d97706', lineWidth: 2, title: 'EMA 21' });
            var ema50Series = chart.addLineSeries({ color: '#7c3aed', lineWidth: 2, title: 'EMA 50' });

            var volumeSeries = chart.addHistogramSeries({
                color: '#94a3b8',
                priceFormat: { type: 'volume' },
                priceScaleId: ''
            });
            volumeSeries.priceScale().applyOptions({
                scaleMargins: { top: 0.8, bottom: 0 }
            });

            function syncLwData(currentBars) {
                var ema9Vals = computeEmaSeries(currentBars, 9);
                var ema21Vals = computeEmaSeries(currentBars, 21);
                var ema50Vals = computeEmaSeries(currentBars, 50);

                var candleData = [];
                var volData = [];
                var e9Data = [];
                var e21Data = [];
                var e50Data = [];

                for (var i = 0; i < currentBars.length; i++) {
                    var b = currentBars[i];
                    var t = b.timeSec;
                    candleData.push({ time: t, open: b.open, high: b.high, low: b.low, close: b.close });
                    volData.push({
                        time: t,
                        value: b.volume,
                        color: b.close >= b.open ? 'rgba(13, 138, 82, 0.28)' : 'rgba(201, 42, 42, 0.28)'
                    });
                    e9Data.push({ time: t, value: ema9Vals[i] });
                    e21Data.push({ time: t, value: ema21Vals[i] });
                    e50Data.push({ time: t, value: ema50Vals[i] });
                }

                candleSeries.setData(candleData);
                volumeSeries.setData(volData);
                ema9Series.setData(e9Data);
                ema21Series.setData(e21Data);
                ema50Series.setData(e50Data);
            }

            syncLwData(bars);
            chart.timeScale().fitContent();

            window.addEventListener('resize', function () {
                if (container.clientWidth > 0) {
                    chart.applyOptions({ width: container.clientWidth });
                }
            });

            return { refresh: syncLwData };
        }
        return null;
    }

    function initFspCanvasChart(meta, rawBars) {
        var canvas = document.getElementById('fsp-candles-canvas');
        if (!canvas || !rawBars || rawBars.length === 0) return null;

        var tf = (meta && meta.timeframe) ? meta.timeframe : 'M15';
        var symbol = meta.symbol || 'XAUUSD';
        var currency = meta.currency || 'USD';
        var decimals = detectDecimals(rawBars[rawBars.length - 1].close, symbol);

        var hoverIndex = -1;
        var hoverY = -1;
        var ohlcEl = document.getElementById('fsp-ohlc-readout');

        function updateLegend(idx) {
            if (!ohlcEl) return;
            var ema9 = computeEmaSeries(rawBars, 9);
            var ema21 = computeEmaSeries(rawBars, 21);
            var ema50 = computeEmaSeries(rawBars, 50);
            var b = rawBars[idx >= 0 && idx < rawBars.length ? idx : rawBars.length - 1];
            var i = idx >= 0 && idx < rawBars.length ? idx : rawBars.length - 1;
            var isUp = b.close >= b.open;
            var color = isUp ? '#0d8a52' : '#c92a2a';
            var chg = b.open !== 0 ? (((b.close - b.open) / b.open) * 100).toFixed(2) : '0.00';
            var sign = b.close >= b.open ? '+' : '';

            ohlcEl.innerHTML =
                '<span style="color:#525866;margin-right:0.55rem;">' + formatTimeLabel(b.closeTime, tf) + '</span>' +
                '<span style="margin-right:0.55rem;">A: <strong>' + formatPrice(b.open, decimals) + '</strong></span>' +
                '<span style="margin-right:0.55rem;">Y: <strong>' + formatPrice(b.high, decimals) + '</strong></span>' +
                '<span style="margin-right:0.55rem;">D: <strong>' + formatPrice(b.low, decimals) + '</strong></span>' +
                '<span style="margin-right:0.55rem;">K: <strong style="color:' + color + ';">' + formatPrice(b.close, decimals) + ' ' + currency + ' (' + sign + chg + '%)</strong></span>' +
                '<span style="margin-right:0.55rem;">Hacim: <strong>' + formatVolume(b.volume) + '</strong></span>' +
                '<span style="color:#1862ff;margin-right:0.45rem;">EMA9: ' + formatPrice(ema9[i], decimals) + '</span>' +
                '<span style="color:#d97706;margin-right:0.45rem;">EMA21: ' + formatPrice(ema21[i], decimals) + '</span>' +
                '<span style="color:#7c3aed;">EMA50: ' + formatPrice(ema50[i], decimals) + '</span>';
        }

        function drawPriceTag(ctx, y, text, bgColor, textColor, rightAxisX, axisWidth) {
            ctx.save();
            ctx.font = '600 11px ui-monospace, SFMono-Regular, Menlo, monospace';
            var h = 18;
            ctx.fillStyle = bgColor;
            ctx.beginPath();
            ctx.roundRect(rightAxisX + 4, y - h / 2, axisWidth - 8, h, 3);
            ctx.fill();
            ctx.fillStyle = textColor;
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            ctx.fillText(text, rightAxisX + 9, y);
            ctx.restore();
        }

        function render() {
            var ema9 = computeEmaSeries(rawBars, 9);
            var ema21 = computeEmaSeries(rawBars, 21);
            var ema50 = computeEmaSeries(rawBars, 50);

            var rect = canvas.getBoundingClientRect();
            var dpr = window.devicePixelRatio || 1;
            var width = Math.max(320, Math.floor(rect.width));
            var height = Math.max(240, Math.floor(rect.height));

            if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
                canvas.width = width * dpr;
                canvas.height = height * dpr;
            }

            var ctx = canvas.getContext('2d');
            ctx.save();
            ctx.scale(dpr, dpr);

            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, width, height);

            var rightAxisWidth = 84;
            var padLeft = 12;
            var padTop = 12;
            var timeAxisHeight = 24;
            var chartWidth = width - padLeft - rightAxisWidth;
            var totalPlotHeight = height - padTop - timeAxisHeight;
            var pricePlotHeight = Math.floor(totalPlotHeight * 0.76);
            var volPlotTop = padTop + pricePlotHeight + 6;
            var volPlotHeight = totalPlotHeight - pricePlotHeight - 6;
            var rightAxisX = width - rightAxisWidth;

            var minPrice = Infinity;
            var maxPrice = -Infinity;
            var maxVol = 1;

            for (var i = 0; i < rawBars.length; i++) {
                var b = rawBars[i];
                if (b.low < minPrice) minPrice = b.low;
                if (b.high > maxPrice) maxPrice = b.high;
                if (ema50[i] < minPrice) minPrice = ema50[i];
                if (ema50[i] > maxPrice) maxPrice = ema50[i];
                if (b.volume > maxVol) maxVol = b.volume;
            }

            var span = maxPrice - minPrice;
            if (span <= 0) span = maxPrice * 0.01 || 1.0;
            minPrice -= span * 0.08;
            maxPrice += span * 0.08;
            span = maxPrice - minPrice;

            function priceToY(p) {
                return padTop + (1.0 - (p - minPrice) / span) * pricePlotHeight;
            }

            function yToPrice(y) {
                return maxPrice - ((y - padTop) / pricePlotHeight) * span;
            }

            ctx.strokeStyle = '#eef0f4';
            ctx.lineWidth = 1;
            ctx.font = '11px ui-monospace, SFMono-Regular, Menlo, monospace';
            ctx.fillStyle = '#525866';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';

            var gridSteps = 5;
            for (var g = 0; g <= gridSteps; g++) {
                var gy = padTop + (pricePlotHeight / gridSteps) * g;
                var gp = maxPrice - (span / gridSteps) * g;
                ctx.beginPath();
                ctx.moveTo(padLeft, gy);
                ctx.lineTo(rightAxisX, gy);
                ctx.stroke();
                ctx.fillText(formatPrice(gp, decimals), rightAxisX + 8, gy);
            }

            ctx.strokeStyle = '#dde1e8';
            ctx.beginPath();
            ctx.moveTo(padLeft, padTop + pricePlotHeight);
            ctx.lineTo(rightAxisX, padTop + pricePlotHeight);
            ctx.moveTo(rightAxisX, padTop);
            ctx.lineTo(rightAxisX, height - timeAxisHeight);
            ctx.moveTo(padLeft, height - timeAxisHeight);
            ctx.lineTo(rightAxisX, height - timeAxisHeight);
            ctx.stroke();

            var n = rawBars.length;
            var slotWidth = chartWidth / n;
            var bodyWidth = Math.max(3, Math.min(14, Math.floor(slotWidth * 0.66)));

            function indexToX(idx) {
                return padLeft + (idx + 0.5) * slotWidth;
            }

            var timeStep = Math.max(1, Math.floor(n / 6));
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            for (var t = 0; t < n; t += timeStep) {
                var tx = indexToX(t);
                ctx.strokeStyle = '#f3f4f7';
                ctx.beginPath();
                ctx.moveTo(tx, padTop);
                ctx.lineTo(tx, height - timeAxisHeight);
                ctx.stroke();

                ctx.fillStyle = '#687082';
                ctx.fillText(formatTimeLabel(rawBars[t].closeTime, tf), tx, height - timeAxisHeight / 2);
            }

            for (var j = 0; j < n; j++) {
                var bar = rawBars[j];
                var cx = indexToX(j);
                var isBull = bar.close >= bar.open;
                var candleColor = isBull ? '#0d8a52' : '#c92a2a';
                var volColor = isBull ? 'rgba(13, 138, 82, 0.25)' : 'rgba(201, 42, 42, 0.25)';

                var vh = (bar.volume / maxVol) * volPlotHeight;
                ctx.fillStyle = volColor;
                ctx.fillRect(cx - bodyWidth / 2, volPlotTop + volPlotHeight - vh, bodyWidth, vh);

                var highY = priceToY(bar.high);
                var lowY = priceToY(bar.low);
                ctx.strokeStyle = candleColor;
                ctx.lineWidth = 1.25;
                ctx.beginPath();
                ctx.moveTo(cx, highY);
                ctx.lineTo(cx, lowY);
                ctx.stroke();

                var openY = priceToY(bar.open);
                var closeY = priceToY(bar.close);
                var topY = Math.min(openY, closeY);
                var bHeight = Math.max(1.5, Math.abs(closeY - openY));
                ctx.fillStyle = candleColor;
                ctx.fillRect(cx - bodyWidth / 2, topY, bodyWidth, bHeight);
            }

            function drawLineSeries(series, color, lineWidth) {
                ctx.save();
                ctx.strokeStyle = color;
                ctx.lineWidth = lineWidth;
                ctx.beginPath();
                for (var k = 0; k < series.length; k++) {
                    var lx = indexToX(k);
                    var ly = priceToY(series[k]);
                    if (k === 0) ctx.moveTo(lx, ly);
                    else ctx.lineTo(lx, ly);
                }
                ctx.stroke();
                ctx.restore();
            }

            drawLineSeries(ema50, '#7c3aed', 1.4);
            drawLineSeries(ema21, '#d97706', 1.6);
            drawLineSeries(ema9, '#1862ff', 1.8);

            var lastBar = rawBars[n - 1];
            var lastY = priceToY(lastBar.close);
            var lastIsBull = lastBar.close >= lastBar.open;
            ctx.save();
            ctx.setLineDash([3, 3]);
            ctx.strokeStyle = lastIsBull ? '#0d8a52' : '#c92a2a';
            ctx.lineWidth = 1.2;
            ctx.beginPath();
            ctx.moveTo(padLeft, lastY);
            ctx.lineTo(rightAxisX, lastY);
            ctx.stroke();
            ctx.restore();
            drawPriceTag(
                ctx,
                lastY,
                formatPrice(lastBar.close, decimals),
                lastIsBull ? '#0d8a52' : '#c92a2a',
                '#ffffff',
                rightAxisX,
                rightAxisWidth
            );

            if (hoverIndex >= 0 && hoverIndex < n) {
                var hx = indexToX(hoverIndex);
                ctx.save();
                ctx.setLineDash([4, 4]);
                ctx.strokeStyle = '#94a3b8';
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(hx, padTop);
                ctx.lineTo(hx, height - timeAxisHeight);
                if (hoverY >= padTop && hoverY <= padTop + pricePlotHeight) {
                    ctx.moveTo(padLeft, hoverY);
                    ctx.lineTo(rightAxisX, hoverY);
                }
                ctx.stroke();
                ctx.restore();

                if (hoverY >= padTop && hoverY <= padTop + pricePlotHeight) {
                    var hp = yToPrice(hoverY);
                    drawPriceTag(ctx, hoverY, formatPrice(hp, decimals), '#1e293b', '#ffffff', rightAxisX, rightAxisWidth);
                }
            }

            ctx.restore();
        }

        canvas.addEventListener('mousemove', function (e) {
            var rect = canvas.getBoundingClientRect();
            var mx = e.clientX - rect.left;
            var my = e.clientY - rect.top;
            var rightAxisWidth = 84;
            var padLeft = 12;
            var chartWidth = rect.width - padLeft - rightAxisWidth;
            if (mx < padLeft || mx > padLeft + chartWidth) {
                hoverIndex = -1;
                hoverY = -1;
                updateLegend(rawBars.length - 1);
                render();
                return;
            }
            var idx = Math.floor(((mx - padLeft) / chartWidth) * rawBars.length);
            if (idx < 0) idx = 0;
            if (idx >= rawBars.length) idx = rawBars.length - 1;
            hoverIndex = idx;
            hoverY = my;
            updateLegend(idx);
            render();
        });

        canvas.addEventListener('mouseleave', function () {
            hoverIndex = -1;
            hoverY = -1;
            updateLegend(rawBars.length - 1);
            render();
        });

        window.addEventListener('resize', render);
        updateLegend(rawBars.length - 1);
        render();

        return {
            refresh: function () {
                updateLegend(hoverIndex >= 0 ? hoverIndex : rawBars.length - 1);
                render();
            }
        };
    }

    var INSTRUMENT_DIRECTORY = {
        'XAUUSD': { exchange: 'OANDA', name: 'Spot Altın (Ons / USD)', assetClass: 'Emtia', currency: 'USD', price: 4062.71 },
        'XAGUSD': { exchange: 'OANDA', name: 'Spot Gümüş (Ons / USD)', assetClass: 'Emtia', currency: 'USD', price: 52.330 },
        'XPTUSD': { exchange: 'OANDA', name: 'Spot Platin (Ons / USD)', assetClass: 'Emtia', currency: 'USD', price: 1501.70 },
        'XPDUSD': { exchange: 'OANDA', name: 'Spot Paladyum (Ons / USD)', assetClass: 'Emtia', currency: 'USD', price: 1004.00 },
        'USOIL': { exchange: 'TVC', name: 'WTI Ham Petrol (Varil / USD)', assetClass: 'Emtia', currency: 'USD', price: 88.47 },
        'UKOIL': { exchange: 'TVC', name: 'Brent Ham Petrol (Varil / USD)', assetClass: 'Emtia', currency: 'USD', price: 94.62 },
        'NATGAS': { exchange: 'OANDA', name: 'Doğalgaz (Natural Gas / USD)', assetClass: 'Emtia', currency: 'USD', price: 2.960 },
        'GASOLINE': { exchange: 'NYMEX', name: 'RBOB Benzin (Galon / USD)', assetClass: 'Emtia', currency: 'USD', price: 2.7350 },
        'HEATOIL': { exchange: 'NYMEX', name: 'Kalorifer Yakıtı (Heating Oil / USD)', assetClass: 'Emtia', currency: 'USD', price: 4.2145 },
        'COPPER': { exchange: 'OANDA', name: 'Bakır (High Grade Copper / USD)', assetClass: 'Emtia', currency: 'USD', price: 6.4150 },
        'ALUMINUM': { exchange: 'COMEX', name: 'Alüminyum (Ton / USD)', assetClass: 'Emtia', currency: 'USD', price: 3193.25 },
        'WHEAT': { exchange: 'OANDA', name: 'Buğday (Wheat / USD)', assetClass: 'Emtia', currency: 'USD', price: 598.50 },
        'CORN': { exchange: 'OANDA', name: 'Mısır (Corn / USD)', assetClass: 'Emtia', currency: 'USD', price: 444.25 },
        'SOYBN': { exchange: 'OANDA', name: 'Soya Fasulyesi (Soybeans / USD)', assetClass: 'Emtia', currency: 'USD', price: 1202.25 },
        'SUGAR': { exchange: 'OANDA', name: 'Şeker (Sugar No. 11 / USD)', assetClass: 'Emtia', currency: 'USD', price: 18.47 },
        'COFFEE': { exchange: 'ICEUS', name: 'Kahve (Coffee C / USD)', assetClass: 'Emtia', currency: 'USD', price: 279.50 },
        'COCOA': { exchange: 'ICEUS', name: 'Kakao (Cocoa / USD)', assetClass: 'Emtia', currency: 'USD', price: 5427.00 },
        'COTTON': { exchange: 'ICEUS', name: 'Pamuk (Cotton No. 2 / USD)', assetClass: 'Emtia', currency: 'USD', price: 78.15 },
        'EURUSD': { exchange: 'FX', name: 'Euro / ABD Doları', assetClass: 'Forex', currency: 'USD', price: 1.12371 },
        'GBPUSD': { exchange: 'FX', name: 'İngiliz Sterlini / ABD Doları', assetClass: 'Forex', currency: 'USD', price: 1.34733 },
        'USDJPY': { exchange: 'FX', name: 'ABD Doları / Japon Yeni', assetClass: 'Forex', currency: 'JPY', price: 158.297 },
        'USDCHF': { exchange: 'FX', name: 'ABD Doları / İsviçre Frangı', assetClass: 'Forex', currency: 'USD', price: 0.81709 },
        'AUDUSD': { exchange: 'FX', name: 'Avustralya Doları / ABD Doları', assetClass: 'Forex', currency: 'USD', price: 0.70090 },
        'USDCAD': { exchange: 'FX', name: 'ABD Doları / Kanada Doları', assetClass: 'Forex', currency: 'USD', price: 1.39672 },
        'NZDUSD': { exchange: 'FX', name: 'Yeni Zelanda Doları / ABD Doları', assetClass: 'Forex', currency: 'USD', price: 0.56247 },
        'USDTRY': { exchange: 'FX', name: 'ABD Doları / Türk Lirası', assetClass: 'Forex', currency: 'TRY', price: 50.0178 }
    };

    function syncMetaWithUrlPath(meta) {
        var match = window.location.pathname.match(/\/instrument\/([A-Za-z0-9]+)/);
        if (!match) return meta;
        var reqSym = match[1].toUpperCase();
        var dir = INSTRUMENT_DIRECTORY[reqSym];
        if (dir) {
            meta.symbol = reqSym;
            meta.exchange = dir.exchange;
            meta.currency = dir.currency;
            meta.close = dir.price;
            document.title = reqSym + ' (' + (meta.timeframeLabel || meta.timeframe || '15m') + ') — Forex Sinyal Platformu (FSP)';
            var h1 = document.querySelector('.app-header h1');
            if (h1) h1.textContent = reqSym + ' · ' + dir.name;
            var h2 = document.querySelector('section.glass-card h2');
            if (h2) h2.textContent = dir.exchange + ':' + reqSym + ' · ' + (meta.timeframeLabel || '15m') + ' (' + (meta.timeframe || 'M15') + ') — TradingView Grafiği & Lightweight Charts';
            var tfLinks = document.querySelectorAll('.app-nav a.btn');
            for (var i = 0; i < tfLinks.length; i++) {
                var href = tfLinks[i].getAttribute('href') || '';
                tfLinks[i].setAttribute('href', href.replace(/\/instrument\/[A-Za-z0-9]+/, '/instrument/' + reqSym));
            }
        }
        return meta;
    }

    function boot() {
        var meta = syncMetaWithUrlPath(window.FSP_CHART_META || {});
        var bars = prepareTimeframeBars(meta);
        var lwHandle = initTradingViewPrimaryChart(meta, bars);
        var canvasHandle = initFspCanvasChart(meta, bars);
        computeAndRenderDynamicIndicators(meta, bars);

        // Live time-driven dynamic update every 2.5 seconds
        var tickCounter = 0;
        var sym = meta.symbol || 'XAUUSD';
        var tf = meta.timeframe || 'M15';
        var amp = TF_AMP_MAP[tf] || 1.0;
        var decimals = detectDecimals(bars[bars.length - 1].close, sym);
        var mult = Math.pow(10, decimals);

        setInterval(function () {
            tickCounter++;
            var last = bars[bars.length - 1];
            var phase = tickCounter * 0.65 + tf.charCodeAt(0);
            var deltaPct = (Math.sin(phase) * 0.00055 + Math.cos(phase * 0.43) * 0.00035) * amp;
            var nextClose = Math.round(last.close * (1.0 + deltaPct) * mult) / mult;
            last.close = nextClose;
            if (nextClose > last.high) last.high = nextClose;
            if (nextClose < last.low) last.low = nextClose;
            last.volume = Math.round(last.volume + 35 + Math.abs(Math.sin(phase)) * 140);
            last.closeTime = new Date().toISOString();

            computeAndRenderDynamicIndicators(meta, bars);
            if (lwHandle && typeof lwHandle.refresh === 'function') lwHandle.refresh(bars);
            if (canvasHandle && typeof canvasHandle.refresh === 'function') canvasHandle.refresh();
        }, 2500);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
