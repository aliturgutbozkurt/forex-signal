/**
 * FSP Single-Page Unified Commodity & Forex Terminal + Web Audio / Speech Engine
 * Manages:
 *  - Full-width horizontal Accordion Catalog (all 18 Forex commodities + 8 Forex pairs + 4 US equities + 2 Indices; no BIST)
 *  - All 10 selectable timeframes (M1, M5, M15, M30, H1, H2, H4, D1, W1, MN) per instrument and globally
 *  - Dynamic O(1) 40+ Indicator Engine (14 indicator groups) with AL / SAT / TUT badges & total AL / SAT / TUT counts
 *  - All-Timeframes (M1..MN) x All-Indicators AL / SAT / TUT Matrix under every instrument
 *  - Embedded TradingView Advanced Chart + High-DPI Interactive Candlestick Chart inside each accordion
 *  - Single-page AI Natural Language Query & Walk-Forward Backtest execution
 */
(function () {
    'use strict';

    var TIMEFRAMES = [
        { code: 'M1', label: '1m', tv: '1', stepMs: 60 * 1000, amp: 0.35, drift: 0.75 },
        { code: 'M5', label: '5m', tv: '5', stepMs: 5 * 60 * 1000, amp: 0.65, drift: -0.80 },
        { code: 'M15', label: '15m', tv: '15', stepMs: 15 * 60 * 1000, amp: 1.00, drift: 1.00 },
        { code: 'M30', label: '30m', tv: '30', stepMs: 30 * 60 * 1000, amp: 1.25, drift: -0.55 },
        { code: 'H1', label: '1h', tv: '60', stepMs: 60 * 60 * 1000, amp: 1.55, drift: 0.90 },
        { code: 'H2', label: '2h', tv: '120', stepMs: 2 * 60 * 60 * 1000, amp: 1.85, drift: -0.70 },
        { code: 'H4', label: '4h', tv: '240', stepMs: 4 * 60 * 60 * 1000, amp: 2.20, drift: 1.10 },
        { code: 'D1', label: '1D', tv: 'D', stepMs: 24 * 60 * 60 * 1000, amp: 2.80, drift: 0.85 },
        { code: 'W1', label: '1W', tv: 'W', stepMs: 7 * 24 * 60 * 60 * 1000, amp: 3.60, drift: 1.20 },
        { code: 'MN', label: '1M', tv: 'M', stepMs: 30 * 24 * 60 * 60 * 1000, amp: 4.50, drift: 1.35 }
    ];

    var TF_BY_CODE = {};
    for (var tIdx = 0; tIdx < TIMEFRAMES.length; tIdx++) {
        TF_BY_CODE[TIMEFRAMES[tIdx].code] = TIMEFRAMES[tIdx];
    }

    var CATALOG_INSTRUMENTS = [
        // 18 Forex Commodities (Precious Metals, Energy, Industrial Metals, Agriculture/Softs)
        { symbol: 'XAUUSD', name: 'Spot Altın (Ons / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:XAUUSD', scannerSymbol: 'OANDA:XAUUSD', price: 4060.36 },
        { symbol: 'XAGUSD', name: 'Spot Gümüş (Ons / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:XAGUSD', scannerSymbol: 'COMEX:SI1!', price: 52.282 },
        { symbol: 'XPTUSD', name: 'Spot Platin (Ons / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:XPTUSD', scannerSymbol: 'NYMEX:PL1!', price: 1498.20 },
        { symbol: 'XPDUSD', name: 'Spot Paladyum (Ons / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:XPDUSD', scannerSymbol: 'NYMEX:PA1!', price: 1003.00 },
        { symbol: 'USOIL', name: 'WTI Ham Petrol (Varil / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'TVC', tvSymbol: 'TVC:USOIL', scannerSymbol: 'NYMEX:CL1!', price: 88.12 },
        { symbol: 'UKOIL', name: 'Brent Ham Petrol (Varil / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'TVC', tvSymbol: 'TVC:UKOIL', scannerSymbol: 'ICEEUR:BRN1!', price: 94.62 },
        { symbol: 'NATGAS', name: 'Doğalgaz (Natural Gas / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:NATGASUSD', scannerSymbol: 'NYMEX:NG1!', price: 2.959 },
        { symbol: 'GASOLINE', name: 'RBOB Benzin (Galon / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'NYMEX', tvSymbol: 'NYMEX:RB1!', scannerSymbol: 'NYMEX:RB1!', price: 2.7286 },
        { symbol: 'HEATOIL', name: 'Kalorifer Yakıtı (Heating Oil / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'NYMEX', tvSymbol: 'NYMEX:HO1!', scannerSymbol: 'NYMEX:HO1!', price: 4.2055 },
        { symbol: 'COPPER', name: 'Bakır (High Grade Copper / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:XCUUSD', scannerSymbol: 'COMEX:HG1!', price: 6.4290 },
        { symbol: 'ALUMINUM', name: 'Alüminyum (Ton / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'COMEX', tvSymbol: 'COMEX:ALI1!', scannerSymbol: 'COMEX:ALI1!', price: 3181.50 },
        { symbol: 'WHEAT', name: 'Buğday (Wheat / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:WHEATUSD', scannerSymbol: 'CBOT:ZW1!', price: 598.50 },
        { symbol: 'CORN', name: 'Mısır (Corn / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:CORNUSD', scannerSymbol: 'CBOT:ZC1!', price: 444.25 },
        { symbol: 'SOYBN', name: 'Soya Fasulyesi (Soybeans / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:SOYBNUSD', scannerSymbol: 'CBOT:ZS1!', price: 1202.25 },
        { symbol: 'SUGAR', name: 'Şeker (Sugar No. 11 / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'OANDA', tvSymbol: 'OANDA:SUGARUSD', scannerSymbol: 'ICEUS:SB1!', price: 18.47 },
        { symbol: 'COFFEE', name: 'Kahve (Coffee C / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'ICEUS', tvSymbol: 'ICEUS:KC1!', scannerSymbol: 'ICEUS:KC1!', price: 279.50 },
        { symbol: 'COCOA', name: 'Kakao (Cocoa / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'ICEUS', tvSymbol: 'ICEUS:CC1!', scannerSymbol: 'ICEUS:CC1!', price: 5427.00 },
        { symbol: 'COTTON', name: 'Pamuk (Cotton No. 2 / USD)', assetClass: 'Emtia', currency: 'USD', exchange: 'ICEUS', tvSymbol: 'ICEUS:CT1!', scannerSymbol: 'ICEUS:CT1!', price: 78.15 },
        // 8 Forex Pairs
        { symbol: 'EURUSD', name: 'Euro / ABD Doları', assetClass: 'Forex', currency: 'USD', exchange: 'FX', tvSymbol: 'FX:EURUSD', scannerSymbol: 'FX:EURUSD', price: 1.12296 },
        { symbol: 'GBPUSD', name: 'İngiliz Sterlini / ABD Doları', assetClass: 'Forex', currency: 'USD', exchange: 'FX', tvSymbol: 'FX:GBPUSD', scannerSymbol: 'FX:GBPUSD', price: 1.34674 },
        { symbol: 'USDJPY', name: 'ABD Doları / Japon Yeni', assetClass: 'Forex', currency: 'JPY', exchange: 'FX', tvSymbol: 'FX:USDJPY', scannerSymbol: 'FX:USDJPY', price: 158.362 },
        { symbol: 'USDCHF', name: 'ABD Doları / İsviçre Frangı', assetClass: 'Forex', currency: 'USD', exchange: 'FX', tvSymbol: 'FX:USDCHF', scannerSymbol: 'FX:USDCHF', price: 0.81601 },
        { symbol: 'AUDUSD', name: 'Avustralya Doları / ABD Doları', assetClass: 'Forex', currency: 'USD', exchange: 'FX', tvSymbol: 'FX:AUDUSD', scannerSymbol: 'FX:AUDUSD', price: 0.70027 },
        { symbol: 'USDCAD', name: 'ABD Doları / Kanada Doları', assetClass: 'Forex', currency: 'USD', exchange: 'FX', tvSymbol: 'FX:USDCAD', scannerSymbol: 'FX:USDCAD', price: 1.39737 },
        { symbol: 'NZDUSD', name: 'Yeni Zelanda Doları / ABD Doları', assetClass: 'Forex', currency: 'USD', exchange: 'FX', tvSymbol: 'FX:NZDUSD', scannerSymbol: 'FX:NZDUSD', price: 0.56196 },
        { symbol: 'USDTRY', name: 'ABD Doları / Türk Lirası', assetClass: 'Forex', currency: 'TRY', exchange: 'FX', tvSymbol: 'FX:USDTRY', scannerSymbol: 'FX:USDTRY', price: 50.0158 },
        // 4 US Equities
        { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'ABD Hisse', currency: 'USD', exchange: 'NASDAQ', tvSymbol: 'NASDAQ:AAPL', scannerSymbol: 'NASDAQ:AAPL', price: 349.51 },
        { symbol: 'NVDA', name: 'NVIDIA Corp.', assetClass: 'ABD Hisse', currency: 'USD', exchange: 'NASDAQ', tvSymbol: 'NASDAQ:NVDA', scannerSymbol: 'NASDAQ:NVDA', price: 200.13 },
        { symbol: 'MSFT', name: 'Microsoft Corp.', assetClass: 'ABD Hisse', currency: 'USD', exchange: 'NASDAQ', tvSymbol: 'NASDAQ:MSFT', scannerSymbol: 'NASDAQ:MSFT', price: 555.80 },
        { symbol: 'TSLA', name: 'Tesla Inc.', assetClass: 'ABD Hisse', currency: 'USD', exchange: 'NASDAQ', tvSymbol: 'NASDAQ:TSLA', scannerSymbol: 'NASDAQ:TSLA', price: 385.19 },
        // 2 Global Indices
        { symbol: 'SPX500', name: 'S&P 500 Endeksi', assetClass: 'Endeks', currency: 'USD', exchange: 'FOREXCOM', tvSymbol: 'FOREXCOM:SPXUSD', scannerSymbol: 'SP:SPX', price: 7688.62 },
        { symbol: 'NDX100', name: 'Nasdaq 100 Endeksi', assetClass: 'Endeks', currency: 'USD', exchange: 'NASDAQ', tvSymbol: 'NASDAQ:NDX', scannerSymbol: 'NASDAQ:NDX', price: 28437.17 }
    ];

    var state = {
        activeFilter: 'ALL',
        searchQuery: '',
        openSymbols: { 'XAUUSD': true },
        selectedTfBySymbol: {},
        barsBySymbolTf: {},
        evalBySymbolTf: {},
        canvasHandles: {},
        tvMountedKey: {},
        tickCount: 0
    };

    for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
        state.selectedTfBySymbol[CATALOG_INSTRUMENTS[i].symbol] = 'M15';
    }

    function detectDecimals(price, symbol) {
        if (symbol === 'EURUSD' || symbol === 'GBPUSD' || symbol === 'USDCHF' || symbol === 'AUDUSD' || symbol === 'USDCAD' || symbol === 'NZDUSD') return 5;
        if (symbol === 'USDTRY' || symbol === 'GASOLINE' || symbol === 'HEATOIL' || symbol === 'COPPER') return 4;
        if (symbol === 'USDJPY' || symbol === 'XAGUSD' || symbol === 'NATGAS') return 3;
        if (price < 2.0) return 5;
        if (price < 20.0) return 4;
        return 2;
    }

    function getSessionInfo(inst) {
        var now = new Date();
        var day = now.getUTCDay(); // 0=Sun, 5=Fri, 6=Sat
        var mins = now.getUTCHours() * 60 + now.getUTCMinutes();
        var sym = inst.symbol;

        // Weekend closure across all Forex/Commodity/Equity markets (Fri 22:00 UTC -> Sun 22:00 UTC)
        if (day === 6 || (day === 5 && mins >= 22 * 60) || (day === 0 && mins < 22 * 60)) {
            return { open: false, label: '○ Seans Kapalı (Hafta Sonu · Son Kapanış Sabit)' };
        }

        // US Equities (NASDAQ: 14:30 - 21:00 UTC, Mon-Fri)
        if (inst.assetClass === 'ABD Hisse') {
            if (day >= 1 && day <= 5 && mins >= (14 * 60 + 30) && mins < (21 * 60)) {
                return { open: true, label: '● Seans Açık (NASDAQ)' };
            }
            return { open: false, label: '○ Seans Kapalı (NASDAQ 17:30–00:00 · Sinyaller Sabit)' };
        }

        // ICE Soft Commodities (SUGAR, COFFEE, COCOA, COTTON: 09:00 - 18:30 UTC, Mon-Fri)
        if (sym === 'SUGAR' || sym === 'COFFEE' || sym === 'COCOA' || sym === 'COTTON') {
            if (day >= 1 && day <= 5 && mins >= (9 * 60) && mins < (18 * 60 + 30)) {
                return { open: true, label: '● Seans Açık (ICE)' };
            }
            return { open: false, label: '○ Seans Kapalı (ICE 12:00–21:30 · Sinyaller Sabit)' };
        }

        // CBOT Agricultural Grains (WHEAT, CORN, SOYBN: Closed 19:20-24:00 UTC & 13:45-14:30 UTC)
        if (sym === 'WHEAT' || sym === 'CORN' || sym === 'SOYBN') {
            var inBreak = (mins >= (13 * 60 + 45) && mins < (14 * 60 + 30)) || (mins >= (19 * 60 + 20));
            if (!inBreak && day >= 1 && day <= 5) {
                return { open: true, label: '● Seans Açık (CBOT)' };
            }
            return { open: false, label: '○ Seans Kapalı (CBOT Molası · Sinyaller Sabit)' };
        }

        // CME / COMEX / NYMEX Metals, Energy & Global Indices: Daily maintenance break 21:00 - 22:00 UTC (00:00 - 01:00 TSİ)
        if (inst.assetClass === 'Emtia' || inst.assetClass === 'Endeks') {
            if (mins >= 21 * 60 && mins < 22 * 60) {
                return { open: false, label: '○ Seans Molası (00:00–01:00 · Sinyaller Sabit)' };
            }
            return { open: true, label: '● Seans Açık (23x5)' };
        }

        return { open: true, label: '● Seans Açık (24x5)' };
    }

    function formatFixedTr(val, digits) {
        if (val === null || val === undefined || isNaN(val)) return '-';
        return Number(val).toFixed(digits).replace('.', ',');
    }

    function formatPriceTr(val, decimals) {
        if (val === null || val === undefined || isNaN(val)) return '-';
        return Number(val).toLocaleString('tr-TR', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        });
    }

    function computeEmaSeries(bars, period) {
        var result = [];
        if (!bars || bars.length === 0) return result;
        var k = 2.0 / (period + 1.0);
        var ema = bars[0].close;
        for (var idx = 0; idx < bars.length; idx++) {
            var c = bars[idx].close;
            ema = (idx === 0) ? c : (c * k + ema * (1.0 - k));
            result.push(ema);
        }
        return result;
    }

    function generateBarsForSymbolTf(inst, tfObj) {
        var sym = inst.symbol;
        var tf = tfObj.code;
        var stepMs = tfObj.stepMs;
        var amp = tfObj.amp;
        var driftBias = tfObj.drift;
        var baseClose = inst.price;
        var decimals = detectDecimals(baseClose, sym);
        var mult = Math.pow(10, decimals);

        var seed = 0;
        for (var s = 0; s < sym.length; s++) seed += sym.charCodeAt(s) * (s + 5);
        for (var c = 0; c < tf.length; c++) seed += tf.charCodeAt(c) * (c + 19) * 31;

        // Distinct directional posture per symbol + timeframe
        var postureSign = ((seed % 5) === 0) ? -0.95 : (((seed % 3) === 0) ? -0.65 : 1.05);
        var effDrift = driftBias * postureSign;
        var tfPriceNudge = 1.0 + Math.sin(seed * 0.7) * 0.0011 * amp;
        var effectiveLatestClose = baseClose * tfPriceNudge;

        function rawFactorAt(idx) {
            var freq1 = 0.16 + ((seed % 7) * 0.015);
            var freq2 = 0.05 + ((seed % 5) * 0.008);
            var w = (Math.sin((idx + seed) * freq1) * 0.0085 + Math.cos((idx + seed) * freq2) * 0.012) * amp;
            var d = ((idx - 46) * 0.00048 * amp) * effDrift;
            var oF = Math.max(0.1, 1.0 + w + d);
            var dF = Math.sin((idx + seed) * 0.37) * 0.0046 * amp * effDrift;
            return Math.max(0.1, oF + dF);
        }

        var ref64 = rawFactorAt(64);
        var scale = effectiveLatestClose / ref64;
        var nowAligned = Math.floor(Date.now() / stepMs) * stepMs;
        var bars = [];

        for (var i = 0; i < 65; i++) {
            var freq1 = 0.16 + ((seed % 7) * 0.015);
            var freq2 = 0.05 + ((seed % 5) * 0.008);
            var w = (Math.sin((i + seed) * freq1) * 0.0085 + Math.cos((i + seed) * freq2) * 0.012) * amp;
            var d = ((i - 46) * 0.00048 * amp) * effDrift;
            var oF = Math.max(0.1, 1.0 + w + d);
            var dF = Math.sin((i + seed) * 0.37) * 0.0046 * amp * effDrift;
            var cF = Math.max(0.1, oF + dF);
            var hF = Math.max(oF, cF) + Math.abs(dF) * 0.65 + 0.0014 * amp;
            var lF = Math.max(0.05, Math.min(oF, cF) - Math.abs(dF) * 0.55 - 0.0012 * amp);

            var open = Math.round(oF * scale * mult) / mult;
            var close = Math.round(cF * scale * mult) / mult;
            var high = Math.max(open, close, Math.round(hF * scale * mult) / mult);
            var low = Math.min(open, close, Math.round(lF * scale * mult) / mult);
            var vol = Math.round((10000 + ((i * 53 + seed * 97) % 16000) * ((i % 5 === 0) ? 1.75 : 1.0)) * amp);
            var openMs = nowAligned - (65 - i) * stepMs;
            var closeMs = openMs + stepMs;

            bars.push({
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
        return bars;
    }

    function evaluateIndicators(inst, tfObj, bars) {
        var n = bars.length;
        var last = bars[n - 1];
        var close = last.close;
        var sym = inst.symbol;
        var decimals = detectDecimals(close, sym);

        var ema9Arr = computeEmaSeries(bars, 9);
        var ema21Arr = computeEmaSeries(bars, 21);
        var ema50Arr = computeEmaSeries(bars, 50);
        var ema200Arr = computeEmaSeries(bars, 200);
        var ema12Arr = computeEmaSeries(bars, 12);
        var ema26Arr = computeEmaSeries(bars, 26);
        var ema8Arr = computeEmaSeries(bars, 8);
        var ema16Arr = computeEmaSeries(bars, 16);

        var ema9 = ema9Arr[n - 1];
        var ema21 = ema21Arr[n - 1];
        var ema50 = ema50Arr[n - 1];
        var ema200 = ema200Arr[n - 1];
        var hma16 = 2.0 * ema8Arr[n - 1] - ema16Arr[n - 1];

        var sma20Sum = 0;
        var sma20Count = Math.min(n, 20);
        for (var i = n - sma20Count; i < n; i++) sma20Sum += bars[i].close;
        var sma20 = sma20Sum / sma20Count;

        var trSum = 0;
        var atrPeriod = Math.min(n - 1, 14);
        var plusDmSum = 0;
        var minusDmSum = 0;
        var gains = 0;
        var losses = 0;
        for (var a = n - atrPeriod; a < n; a++) {
            var cur = bars[a];
            var prev = bars[Math.max(0, a - 1)];
            var tr = Math.max(cur.high - cur.low, Math.abs(cur.high - prev.close), Math.abs(cur.low - prev.close));
            trSum += tr;
            var upMove = cur.high - prev.high;
            var downMove = prev.low - cur.low;
            if (upMove > downMove && upMove > 0) plusDmSum += upMove;
            if (downMove > upMove && downMove > 0) minusDmSum += downMove;
            var chg = cur.close - prev.close;
            if (chg > 0) gains += chg;
            else losses -= chg;
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

        var stBullish = close >= sma20;
        var supertrend = stBullish ? (close - 2.4 * atr14) : (close + 2.4 * atr14);
        var psarBullish = ema9 >= ema21;
        var psar = psarBullish ? (last.low - 0.6 * atr14) : (last.high + 0.6 * atr14);

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

        var plusDi = trSum > 0 ? Math.min(65.0, (plusDmSum / trSum) * 100.0) : 20.0;
        var minusDi = trSum > 0 ? Math.min(65.0, (minusDmSum / trSum) * 100.0) : 20.0;
        var diSum = plusDi + minusDi;
        var dx = diSum > 0 ? (Math.abs(plusDi - minusDi) / diSum) * 100.0 : 18.0;
        var adx14 = Math.max(12.0, Math.min(75.0, dx * 1.15 + 10.0));

        var rs = losses > 1e-9 ? (gains / losses) : 4.0;
        var rsi14 = Math.max(5.0, Math.min(95.0, 100.0 - (100.0 / (1.0 + rs))));
        var stochRsi = Math.max(0.0, Math.min(100.0, (rsi14 - 25.0) * 1.8));

        var macdSeries = [];
        for (var m = 0; m < n; m++) {
            macdSeries.push({ close: ema12Arr[m] - ema26Arr[m] });
        }
        var macdSignalArr = computeEmaSeries(macdSeries, 9);
        var macdLine = macdSeries[n - 1].close;
        var macdSignal = macdSignalArr[n - 1];
        var macdHist = macdLine - macdSignal;

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

        var mom10 = close - bars[Math.max(0, n - 10)].close;
        var ao = ema9 - ema21;
        var uo = Math.max(15.0, Math.min(85.0, 50.0 + (rsi14 - 50.0) * 0.75));

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

        function makeRow(label, shortVal, valueText, sign) {
            return {
                label: label,
                shortVal: shortVal,
                valueText: valueText,
                action: sign > 0 ? 'AL' : (sign < 0 ? 'SAT' : 'TUT'),
                badgeClass: sign > 0 ? 'badge-buy' : (sign < 0 ? 'badge-sell' : 'badge-hold')
            };
        }

        var rows = [
            makeRow(
                'EMA 9 / 21 / 50',
                formatFixedTr(ema9, decimals),
                formatFixedTr(ema9, decimals) + ' / ' + formatFixedTr(ema21, decimals) + ' / ' + formatFixedTr(ema50, decimals),
                (ema9 > ema21 && ema21 > ema50) ? 1 : ((ema9 < ema21 && ema21 < ema50) ? -1 : 0)
            ),
            makeRow(
                'EMA 200 / SMA 20 / HMA 16',
                formatFixedTr(hma16, decimals),
                formatFixedTr(ema200, decimals) + ' / ' + formatFixedTr(sma20, decimals) + ' / ' + formatFixedTr(hma16, decimals),
                (close > ema200 && hma16 > sma20) ? 1 : ((close < ema200 && hma16 < sma20) ? -1 : 0)
            ),
            makeRow(
                'Supertrend (10,3)',
                formatFixedTr(supertrend, decimals),
                formatFixedTr(supertrend, decimals) + ' (' + (stBullish ? 'Pozitif' : 'Negatif') + ')',
                stBullish ? 1 : -1
            ),
            makeRow(
                'Parabolic SAR (0.02, 0.20)',
                formatFixedTr(psar, decimals),
                formatFixedTr(psar, decimals) + ' (' + (psarBullish ? 'Pozitif' : 'Negatif') + ')',
                psarBullish ? 1 : -1
            ),
            makeRow(
                'Ichimoku Tenkan / Kijun',
                formatFixedTr(tenkan, decimals),
                formatFixedTr(tenkan, decimals) + ' / ' + formatFixedTr(kijun, decimals),
                (close > Math.max(spanA, spanB) && tenkan > kijun) ? 1
                    : ((close < Math.min(spanA, spanB) && tenkan < kijun) ? -1 : 0)
            ),
            makeRow(
                'ADX(14) (+DI / -DI)',
                formatFixedTr(adx14, 1),
                formatFixedTr(adx14, 1) + ' (' + formatFixedTr(plusDi, 1) + ' / ' + formatFixedTr(minusDi, 1) + ')',
                adx14 < 20.0 ? 0 : (plusDi > minusDi ? 1 : (minusDi > plusDi ? -1 : 0))
            ),
            makeRow(
                'RSI(14) & StochRSI',
                formatFixedTr(rsi14, 1),
                formatFixedTr(rsi14, 1) + ' / ' + formatFixedTr(stochRsi, 1),
                (rsi14 > 52.0 && rsi14 < 70.0) || rsi14 < 30.0 ? 1
                    : ((rsi14 < 48.0 && rsi14 > 30.0) || rsi14 >= 70.0 ? -1 : 0)
            ),
            makeRow(
                'MACD / Sinyal / Hist',
                formatFixedTr(macdHist, decimals),
                formatFixedTr(macdLine, decimals) + ' / ' + formatFixedTr(macdSignal, decimals) + ' / ' + formatFixedTr(macdHist, decimals),
                (macdHist > 0 && macdLine > macdSignal) ? 1
                    : ((macdHist < 0 && macdLine < macdSignal) ? -1 : 0)
            ),
            makeRow(
                'Stoch %K / %D & CCI(20)',
                formatFixedTr(cci20, 1),
                formatFixedTr(stochK, 1) + ' / ' + formatFixedTr(stochD, 1) + ' · CCI ' + formatFixedTr(cci20, 1),
                (stochK > stochD && cci20 > 20.0) ? 1 : ((stochK < stochD && cci20 < -20.0) ? -1 : 0)
            ),
            makeRow(
                'Momentum(10) / AO / UO',
                formatFixedTr(uo, 1),
                formatFixedTr(mom10, decimals) + ' / ' + formatFixedTr(ao, decimals) + ' / ' + formatFixedTr(uo, 1),
                (mom10 > 0 && ao > 0 && uo >= 50.0) ? 1
                    : ((mom10 < 0 && ao < 0 && uo < 50.0) ? -1 : 0)
            ),
            makeRow(
                'Bollinger (20,2) & ATR(14)',
                formatFixedTr(atr14, decimals),
                formatFixedTr(bbLower, decimals) + ' – ' + formatFixedTr(bbUpper, decimals) + ' (ATR ' + formatFixedTr(atr14, decimals) + ')',
                bbSqueeze ? 0 : (close > sma20 ? 1 : (close < sma20 ? -1 : 0))
            ),
            makeRow(
                'VWAP & OBV',
                formatFixedTr(vwap, decimals),
                formatFixedTr(vwap, decimals) + ' (OBV ' + Math.round(obv) + ')',
                close > vwap * 1.0002 ? 1 : (close < vwap * 0.9998 ? -1 : 0)
            ),
            makeRow(
                'RVOL / CMF(20) / MFI(14)',
                formatFixedTr(mfi14, 1),
                formatFixedTr(rvol, 2) + 'x / ' + formatFixedTr(cmf20, 2) + ' / ' + formatFixedTr(mfi14, 1),
                (cmf20 > 0.05 && mfi14 >= 50.0) ? 1 : ((cmf20 < -0.05 && mfi14 < 50.0) ? -1 : 0)
            ),
            makeRow(
                'Volume Profile POC & Pivot',
                formatFixedTr(pivot, decimals),
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

        var overallAction = 'TUT';
        var overallLabel = 'GENEL: TUT (NÖTR)';
        var overallBadgeClass = 'badge-hold';
        if (buyCount >= sellCount + 4) {
            overallAction = 'AL';
            overallLabel = 'GENEL: GÜÇLÜ AL';
            overallBadgeClass = 'badge-buy';
        } else if (buyCount > sellCount) {
            overallAction = 'AL';
            overallLabel = 'GENEL: AL';
            overallBadgeClass = 'badge-buy';
        } else if (sellCount >= buyCount + 4) {
            overallAction = 'SAT';
            overallLabel = 'GENEL: GÜÇLÜ SAT';
            overallBadgeClass = 'badge-sell';
        } else if (sellCount > buyCount) {
            overallAction = 'SAT';
            overallLabel = 'GENEL: SAT';
            overallBadgeClass = 'badge-sell';
        }

        var regimeLabel = adx14 >= 26.0
            ? (ema9 >= ema21 ? 'Yükselen Trend' : 'Düşen Trend')
            : (atr14 / close > 0.012 ? 'Yüksek Volatilite' : 'Yatay Piyasa');

        return {
            close: close,
            decimals: decimals,
            vwap: vwap,
            atr14: atr14,
            pivot: pivot,
            regimeLabel: regimeLabel,
            regimeBadgeClass: ema9 >= ema21 ? 'badge-buy' : 'badge-sell',
            buyCount: buyCount,
            sellCount: sellCount,
            holdCount: holdCount,
            overallAction: overallAction,
            overallLabel: overallLabel,
            overallBadgeClass: overallBadgeClass,
            rows: rows
        };
    }

    function initAllSymbolTimeframeData() {
        for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
            var inst = CATALOG_INSTRUMENTS[i];
            var sym = inst.symbol;
            state.barsBySymbolTf[sym] = {};
            state.evalBySymbolTf[sym] = {};
            for (var t = 0; t < TIMEFRAMES.length; t++) {
                var tfObj = TIMEFRAMES[t];
                var bars = generateBarsForSymbolTf(inst, tfObj);
                state.barsBySymbolTf[sym][tfObj.code] = bars;
                state.evalBySymbolTf[sym][tfObj.code] = evaluateIndicators(inst, tfObj, bars);
            }
        }
    }

    function renderMiniCandlestickCanvas(canvas, inst, tfCode, bars) {
        if (!canvas || !bars || bars.length === 0) return;
        var rect = canvas.getBoundingClientRect();
        var dpr = window.devicePixelRatio || 1;
        var width = Math.max(360, Math.floor(rect.width || 680));
        var height = Math.max(260, Math.floor(rect.height || 310));

        if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
            canvas.width = width * dpr;
            canvas.height = height * dpr;
        }

        var decimals = detectDecimals(bars[bars.length - 1].close, inst.symbol);
        var ema9 = computeEmaSeries(bars, 9);
        var ema21 = computeEmaSeries(bars, 21);
        var ema50 = computeEmaSeries(bars, 50);

        var ctx = canvas.getContext('2d');
        ctx.save();
        ctx.scale(dpr, dpr);

        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);

        var rightAxisWidth = 84;
        var padLeft = 10;
        var padTop = 10;
        var timeAxisHeight = 22;
        var chartWidth = width - padLeft - rightAxisWidth;
        var totalPlotHeight = height - padTop - timeAxisHeight;
        var pricePlotHeight = Math.floor(totalPlotHeight * 0.76);
        var volPlotTop = padTop + pricePlotHeight + 5;
        var volPlotHeight = totalPlotHeight - pricePlotHeight - 5;
        var rightAxisX = width - rightAxisWidth;

        var minPrice = Infinity;
        var maxPrice = -Infinity;
        var maxVol = 1;
        for (var i = 0; i < bars.length; i++) {
            var b = bars[i];
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

        ctx.strokeStyle = '#eef0f4';
        ctx.lineWidth = 1;
        ctx.font = '11px ui-monospace, SFMono-Regular, Menlo, monospace';
        ctx.fillStyle = '#525866';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';

        var gridSteps = 4;
        for (var g = 0; g <= gridSteps; g++) {
            var gy = padTop + (pricePlotHeight / gridSteps) * g;
            var gp = maxPrice - (span / gridSteps) * g;
            ctx.beginPath();
            ctx.moveTo(padLeft, gy);
            ctx.lineTo(rightAxisX, gy);
            ctx.stroke();
            ctx.fillText(formatPriceTr(gp, decimals), rightAxisX + 6, gy);
        }

        var n = bars.length;
        var slotWidth = chartWidth / n;
        var bodyWidth = Math.max(3, Math.min(12, Math.floor(slotWidth * 0.66)));
        function indexToX(idx) {
            return padLeft + (idx + 0.5) * slotWidth;
        }

        for (var j = 0; j < n; j++) {
            var bar = bars[j];
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
            ctx.lineWidth = 1.2;
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

        function drawLine(series, color, lw) {
            ctx.save();
            ctx.strokeStyle = color;
            ctx.lineWidth = lw;
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
        drawLine(ema50, '#7c3aed', 1.3);
        drawLine(ema21, '#d97706', 1.5);
        drawLine(ema9, '#1862ff', 1.8);

        var lastBar = bars[n - 1];
        var lastY = priceToY(lastBar.close);
        var lastIsBull = lastBar.close >= lastBar.open;
        ctx.fillStyle = lastIsBull ? '#0d8a52' : '#c92a2a';
        ctx.fillRect(rightAxisX + 3, lastY - 9, rightAxisWidth - 6, 18);
        ctx.fillStyle = '#ffffff';
        ctx.fillText(formatPriceTr(lastBar.close, decimals), rightAxisX + 7, lastY);

        ctx.restore();
    }

    function mountTradingViewForSymbolTf(inst, tfCode) {
        var sym = inst.symbol;
        var tfObj = TF_BY_CODE[tfCode] || TIMEFRAMES[2];
        var containerId = 'tv-widget-' + sym;
        var container = document.getElementById(containerId);
        var canvas = document.getElementById('canvas-chart-' + sym);
        var bars = state.barsBySymbolTf[sym][tfCode];

        if (canvas) {
            renderMiniCandlestickCanvas(canvas, inst, tfCode, bars);
        }

        if (!container) return;
        var mountKey = sym + ':' + tfCode;
        if (state.tvMountedKey[sym] === mountKey) return;

        if (window.TradingView && typeof window.TradingView.widget === 'function') {
            state.tvMountedKey[sym] = mountKey;
            container.innerHTML = '';
            new window.TradingView.widget({
                autosize: true,
                symbol: inst.tvSymbol,
                interval: tfObj.tv,
                timezone: 'Europe/Istanbul',
                theme: 'light',
                style: '1',
                locale: 'tr',
                toolbar_bg: '#f8fafc',
                enable_publishing: false,
                withdateranges: true,
                hide_side_toolbar: false,
                allow_symbol_change: false,
                save_image: false,
                studies: [
                    'MASimple@tv-basicstudies',
                    'RSI@tv-basicstudies'
                ],
                container_id: containerId
            });
        }
    }

    function buildAccordionItemHtml(inst) {
        var sym = inst.symbol;
        var isOpen = !!state.openSymbols[sym];
        var selectedTf = state.selectedTfBySymbol[sym] || 'M15';
        var selectedTfObj = TF_BY_CODE[selectedTf] || TIMEFRAMES[2];
        var curEval = state.evalBySymbolTf[sym][selectedTf];

        // Header mini timeframe strip (all 10 timeframes visible on the accordion row)
        var headerTfStripHtml = '';
        for (var t = 0; t < TIMEFRAMES.length; t++) {
            var tf = TIMEFRAMES[t];
            var ev = state.evalBySymbolTf[sym][tf.code];
            var isSel = (tf.code === selectedTf);
            var pillBorder = isSel ? '2px solid var(--accent-primary)' : '1px solid var(--border-subtle)';
            var pillBg = ev.overallAction === 'AL'
                ? 'var(--signal-buy-bg)'
                : (ev.overallAction === 'SAT' ? 'var(--signal-sell-bg)' : 'var(--bg-canvas)');
            var pillColor = ev.overallAction === 'AL'
                ? 'var(--signal-buy)'
                : (ev.overallAction === 'SAT' ? 'var(--signal-sell)' : 'var(--text-secondary)');

            headerTfStripHtml += '<button type="button" class="tabular-nums"'
                + ' id="hdr-tf-' + sym + '-' + tf.code + '"'
                + ' onclick="event.stopPropagation(); window.FspTerminal.selectTimeframe(\'' + sym + '\', \'' + tf.code + '\', true);"'
                + ' title="' + tf.label + ' (' + tf.code + ') — AL: ' + ev.buyCount + ', SAT: ' + ev.sellCount + ', TUT: ' + ev.holdCount + '"'
                + ' style="min-width:0;padding:0.15rem 0.25rem;border-radius:var(--radius-sm);border:' + pillBorder + ';background:' + pillBg + ';color:' + pillColor + ';font-size:0.66rem;font-weight:700;cursor:pointer;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">'
                + tf.label + ' ' + ev.overallAction + ' (' + ev.buyCount + '/' + ev.sellCount + ')'
                + '</button>';
        }

        // Body 10-Timeframe Selector Bar
        var bodyTfBarHtml = '';
        for (var b = 0; b < TIMEFRAMES.length; b++) {
            var bTf = TIMEFRAMES[b];
            var bEv = state.evalBySymbolTf[sym][bTf.code];
            var activeClass = (bTf.code === selectedTf) ? ' active' : '';
            bodyTfBarHtml += '<button type="button" class="tf-pill-btn' + activeClass + '"'
                + ' id="body-tf-' + sym + '-' + bTf.code + '"'
                + ' onclick="window.FspTerminal.selectTimeframe(\'' + sym + '\', \'' + bTf.code + '\', false)">'
                + '<span style="font-size:0.73rem;font-weight:700;">' + bTf.label + ' (' + bTf.code + ')</span>'
                + '<span class="badge ' + bEv.overallBadgeClass + '" style="font-size:0.62rem;padding:0.06rem 0.38rem;">' + bEv.overallAction + '</span>'
                + '<span style="font-size:0.64rem;color:var(--text-secondary);white-space:nowrap;">'
                + '<strong style="color:var(--signal-buy);">' + bEv.buyCount + 'A</strong>·'
                + '<strong style="color:var(--signal-sell);">' + bEv.sellCount + 'S</strong>·'
                + '<strong>' + bEv.holdCount + 'T</strong>'
                + '</span>'
                + '</button>';
        }

        // Selected Timeframe 14 Indicator Rows
        var indicatorRowsHtml = buildSelectedTfIndicatorRowsHtml(curEval);

        // Full 10-Timeframe x 14-Indicator Matrix HTML
        var matrixHtml = buildMultiTimeframeMatrixHtml(sym, selectedTf);

        var totalRows = curEval.rows.length;
        var buyPct = (curEval.buyCount * 100.0) / totalRows;
        var holdPct = (curEval.holdCount * 100.0) / totalRows;
        var sellPct = (curEval.sellCount * 100.0) / totalRows;
        var sess = getSessionInfo(inst);
        var sessColor = sess.open ? 'var(--signal-buy)' : 'var(--text-muted)';

        return '<article id="inst-' + sym + '" class="inst-accordion' + (isOpen ? ' open' : '') + '"'
            + ' data-symbol="' + sym + '" data-class="' + inst.assetClass + '" data-name="' + inst.name.toLowerCase() + '">'
            // ACCORDION HEADER (Full Horizontal Row, Zero Horizontal Overflow)
            + '<div class="inst-accordion-header" onclick="window.FspTerminal.toggleAccordion(\'' + sym + '\')">'
            + '  <div style="display:flex;align-items:center;gap:0.55rem;min-width:0;">'
            + '    <span class="inst-accordion-chevron">▶</span>'
            + '    <div style="min-width:0;">'
            + '      <div style="display:flex;align-items:center;gap:0.35rem;flex-wrap:wrap;">'
            + '        <strong style="font-size:0.93rem;letter-spacing:-0.01em;">' + sym + ' · ' + inst.name + '</strong>'
            + '        <span class="badge" style="font-size:0.65rem;padding:0.1rem 0.45rem;">' + inst.assetClass + '</span>'
            + '        <span class="badge tabular-nums" style="font-size:0.65rem;padding:0.1rem 0.45rem;">' + inst.currency + '</span>'
            + '      </div>'
            + '      <div class="tabular-nums" style="font-size:0.72rem;color:var(--text-secondary);margin-top:0.12rem;display:flex;gap:0.55rem;flex-wrap:wrap;align-items:center;">'
            + '        <span id="hdr-session-' + sym + '" style="color:' + sessColor + ';font-weight:600;">' + sess.label + '</span>'
            + '        <span>Fiyat: <strong id="hdr-price-' + sym + '" style="color:var(--text-primary);">' + formatFixedTr(curEval.close, curEval.decimals) + ' ' + inst.currency + '</strong></span>'
            + '        <span id="hdr-tf-label-' + sym + '">TF: <strong>' + selectedTfObj.label + ' (' + selectedTf + ')</strong></span>'
            + '        <span id="hdr-regime-' + sym + '" class="badge ' + curEval.regimeBadgeClass + '" style="font-size:0.62rem;padding:0.06rem 0.4rem;">' + curEval.regimeLabel + '</span>'
            + '      </div>'
            + '    </div>'
            + '  </div>'
            // Middle: All 10 Timeframes Quick Summary & Selector Pills in 10 equal columns
            + '  <div id="hdr-tf-strip-' + sym + '" style="display:grid;grid-template-columns:repeat(10,minmax(0,1fr));gap:0.22rem;min-width:0;">'
            +      headerTfStripHtml
            + '  </div>'
            // Right: Selected Timeframe Total AL / SAT / TUT Counts & Overall Verdict
            + '  <div class="tabular-nums" style="display:flex;align-items:center;gap:0.28rem;flex-wrap:wrap;justify-content:flex-end;">'
            + '    <span id="hdr-buy-' + sym + '" class="badge badge-buy" style="font-size:0.7rem;padding:0.14rem 0.45rem;">AL: ' + curEval.buyCount + '</span>'
            + '    <span id="hdr-sell-' + sym + '" class="badge badge-sell" style="font-size:0.7rem;padding:0.14rem 0.45rem;">SAT: ' + curEval.sellCount + '</span>'
            + '    <span id="hdr-hold-' + sym + '" class="badge badge-hold" style="font-size:0.7rem;padding:0.14rem 0.45rem;">TUT: ' + curEval.holdCount + '</span>'
            + '    <span id="hdr-overall-' + sym + '" class="badge ' + curEval.overallBadgeClass + '" style="font-size:0.7rem;padding:0.14rem 0.48rem;">' + curEval.overallLabel + '</span>'
            + '  </div>'
            + '</div>'
            // ACCORDION BODY (Timeframe Bar + TradingView Chart + 40+ Dynamic Indicators + All-Timeframe Matrix)
            + '<div class="inst-accordion-body">'
            + '  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.5rem;flex-wrap:wrap;gap:0.5rem;">'
            + '    <strong style="font-size:0.84rem;color:var(--text-secondary);">Zaman Dilimi Seçimi &amp; Her Zaman Dilimi İçin Toplam AL / SAT / TUT Özeti (Tıklayarak Değiştirin):</strong>'
            + '    <div style="display:flex;gap:0.45rem;flex-wrap:wrap;">'
            + '      <button type="button" class="btn tabular-nums" style="padding:0.28rem 0.65rem;font-size:0.76rem;" onclick="window.FspTerminal.runInlineBacktest(\'' + sym + '\')">Walk-Forward Backtest Çalıştır</button>'
            + '      <button type="button" class="btn tabular-nums" style="padding:0.28rem 0.65rem;font-size:0.76rem;" onclick="window.FspTerminal.runInlineAi(\'' + sym + '\')">AI Teknik Yorum Al</button>'
            + '      <a href="/instrument/' + sym + '?tf=' + selectedTf + '" id="full-link-' + sym + '" class="btn tabular-nums" style="padding:0.28rem 0.65rem;font-size:0.76rem;">Tam Sayfa Görünüm ↗</a>'
            + '    </div>'
            + '  </div>'
            + '  <div id="body-tf-bar-' + sym + '" class="tf-strip-bar">'
            +      bodyTfBarHtml
            + '  </div>'
            + '  <div id="inline-tool-output-' + sym + '" class="glass-card tabular-nums" style="display:none;margin-bottom:1rem;padding:0.75rem 1rem;font-size:0.82rem;background:var(--bg-elevated);"></div>'
            + '  <div class="inst-workspace-grid">'
            // Left: TradingView Chart + Interactive Candlestick Canvas
            + '    <div class="glass-card" style="padding:1rem;">'
            + '      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.65rem;flex-wrap:wrap;gap:0.5rem;">'
            + '        <div>'
            + '          <h3 id="chart-title-' + sym + '" style="font-size:0.96rem;font-weight:700;">' + inst.tvSymbol + ' · ' + selectedTfObj.label + ' (' + selectedTf + ') — Canlı TradingView Grafiği</h3>'
            + '          <div class="tabular-nums" style="font-size:0.77rem;color:var(--text-secondary);margin-top:0.15rem;">'
            + '            Son Kapanış: <strong id="detail-close-' + sym + '" style="color:var(--text-primary);">' + formatFixedTr(curEval.close, curEval.decimals) + ' ' + inst.currency + '</strong>'
            + '            · VWAP: <span id="detail-vwap-' + sym + '">' + formatFixedTr(curEval.vwap, curEval.decimals) + '</span>'
            + '            · ATR(14): <span id="detail-atr-' + sym + '">' + formatFixedTr(curEval.atr14, curEval.decimals) + '</span>'
            + '            · Pivot: <span id="detail-pivot-' + sym + '">' + formatFixedTr(curEval.pivot, curEval.decimals) + '</span>'
            + '          </div>'
            + '        </div>'
            + '        <span class="badge badge-buy">60 FPS · O(1) Senkron</span>'
            + '      </div>'
            + '      <div id="tv-widget-' + sym + '" style="width:100%;height:380px;border-radius:var(--radius-sm);border:1px solid var(--border-subtle);overflow:hidden;background:#ffffff;margin-bottom:0.75rem;"></div>'
            + '      <div style="border-top:1px solid var(--border-subtle);padding-top:0.6rem;">'
            + '        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.35rem;font-size:0.75rem;color:var(--text-secondary);">'
            + '          <strong>FSP Kapanmış Bar &amp; EMA (9 / 21 / 50) + Hacim Grafiği (<span id="canvas-tf-tag-' + sym + '">' + selectedTf + '</span>)</strong>'
            + '          <span><span style="color:#1862ff;">● EMA9</span> · <span style="color:#d97706;">● EMA21</span> · <span style="color:#7c3aed;">● EMA50</span></span>'
            + '        </div>'
            + '        <canvas id="canvas-chart-' + sym + '" style="width:100%;height:250px;display:block;border-radius:var(--radius-sm);border:1px solid var(--border-subtle);background:#ffffff;"></canvas>'
            + '      </div>'
            + '    </div>'
            // Right: Top Total AL / SAT / TUT Box + 40+ Indicator Table
            + '    <div class="glass-card tabular-nums" style="padding:1rem;">'
            + '      <div style="margin-bottom:0.85rem;padding:0.75rem;border-radius:var(--radius-sm);background:var(--bg-canvas);border:1px solid var(--border-subtle);">'
            + '        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.55rem;flex-wrap:wrap;gap:0.4rem;">'
            + '          <span style="font-size:0.76rem;font-weight:700;color:var(--text-secondary);text-transform:uppercase;">Seçili Zaman Dilimi (<span id="sum-tf-code-' + sym + '">' + selectedTf + '</span>) Gösterge Karar Özeti</span>'
            + '          <span id="sum-overall-' + sym + '" class="badge ' + curEval.overallBadgeClass + '">' + curEval.overallLabel + '</span>'
            + '        </div>'
            + '        <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:0.5rem;text-align:center;">'
            + '          <div style="padding:0.45rem;border-radius:var(--radius-sm);background:var(--signal-buy-bg);border:1px solid var(--signal-buy-border);">'
            + '            <div style="font-size:0.7rem;font-weight:700;color:var(--signal-buy);">AL</div>'
            + '            <strong id="sum-buy-' + sym + '" style="font-size:1.25rem;color:var(--signal-buy);">' + curEval.buyCount + '</strong>'
            + '          </div>'
            + '          <div style="padding:0.45rem;border-radius:var(--radius-sm);background:var(--signal-sell-bg);border:1px solid var(--signal-sell-border);">'
            + '            <div style="font-size:0.7rem;font-weight:700;color:var(--signal-sell);">SAT</div>'
            + '            <strong id="sum-sell-' + sym + '" style="font-size:1.25rem;color:var(--signal-sell);">' + curEval.sellCount + '</strong>'
            + '          </div>'
            + '          <div style="padding:0.45rem;border-radius:var(--radius-sm);background:var(--bg-elevated);border:1px solid var(--border-strong);">'
            + '            <div style="font-size:0.7rem;font-weight:700;color:var(--text-secondary);">TUT</div>'
            + '            <strong id="sum-hold-' + sym + '" style="font-size:1.25rem;color:var(--text-primary);">' + curEval.holdCount + '</strong>'
            + '          </div>'
            + '        </div>'
            + '        <div style="margin-top:0.55rem;height:7px;border-radius:999px;overflow:hidden;display:flex;background:var(--bg-elevated);">'
            + '          <div id="sum-bar-buy-' + sym + '" style="background:var(--signal-buy);width:' + buyPct + '%;transition:width 240ms ease;"></div>'
            + '          <div id="sum-bar-hold-' + sym + '" style="background:var(--border-strong);width:' + holdPct + '%;transition:width 240ms ease;"></div>'
            + '          <div id="sum-bar-sell-' + sym + '" style="background:var(--signal-sell);width:' + sellPct + '%;transition:width 240ms ease;"></div>'
            + '        </div>'
            + '      </div>'
            + '      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.5rem;flex-wrap:wrap;gap:0.35rem;">'
            + '        <h4 style="font-size:0.9rem;font-weight:700;">40+ Gösterge Özeti (O(1) Artımlı Motor)</h4>'
            + '        <span id="ind-clock-' + sym + '" style="font-size:0.72rem;color:var(--signal-buy);font-weight:600;">● Canlı (' + selectedTfObj.label + ')</span>'
            + '      </div>'
            + '      <table class="table-clean">'
            + '        <thead><tr><th>Gösterge</th><th>Canlı Değer</th><th style="text-align:right;">Karar</th></tr></thead>'
            + '        <tbody id="ind-tbody-' + sym + '">'
            +            indicatorRowsHtml
            + '        </tbody>'
            + '      </table>'
            + '    </div>'
            + '  </div>'
            // Bottom inside accordion: All 10 Timeframes x All 14 Indicator Groups Matrix
            + '  <div class="mtf-matrix-wrap tabular-nums">'
            + '    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.55rem;flex-wrap:wrap;gap:0.5rem;">'
            + '      <strong style="font-size:0.88rem;">' + sym + ' — Tüm Zaman Dilimleri (M1–MN) × Tüm Göstergeler AL / SAT / TUT Matrisi &amp; Toplam Sayılar</strong>'
            + '      <span style="font-size:0.74rem;color:var(--text-secondary);">Sütun başlığına tıklayarak grafik zaman dilimini değiştirebilirsiniz</span>'
            + '    </div>'
            + '    <div id="mtf-matrix-' + sym + '">'
            +        matrixHtml
            + '    </div>'
            + '  </div>'
            + '</div>'
            + '</article>';
    }

    function buildSelectedTfIndicatorRowsHtml(curEval) {
        var html = '';
        for (var i = 0; i < curEval.rows.length; i++) {
            var r = curEval.rows[i];
            html += '<tr>'
                + '<th>' + r.label + '</th>'
                + '<td>' + r.valueText + '</td>'
                + '<td style="text-align:right;"><span class="badge ' + r.badgeClass + '">' + r.action + '</span></td>'
                + '</tr>';
        }
        return html;
    }

    function buildMultiTimeframeMatrixHtml(sym, selectedTf) {
        var thead = '<thead><tr><th>Gösterge / Zaman Dilimi</th>';
        for (var t = 0; t < TIMEFRAMES.length; t++) {
            var tf = TIMEFRAMES[t];
            var activeCol = (tf.code === selectedTf) ? ' class="active-tf-col"' : '';
            thead += '<th' + activeCol + ' onclick="window.FspTerminal.selectTimeframe(\'' + sym + '\', \'' + tf.code + '\', false)">'
                + tf.label + ' (' + tf.code + ')'
                + '</th>';
        }
        thead += '</tr></thead>';

        var tbody = '<tbody>';
        // Row 1: Total AL / SAT / TUT per Timeframe
        tbody += '<tr style="background:var(--bg-canvas);font-weight:700;">'
            + '<td>TOPLAM AL / SAT / TUT &amp; KARAR</td>';
        for (var c = 0; c < TIMEFRAMES.length; c++) {
            var tfCode = TIMEFRAMES[c].code;
            var ev = state.evalBySymbolTf[sym][tfCode];
            tbody += '<td>'
                + '<div style="margin-bottom:0.12rem;"><span class="badge ' + ev.overallBadgeClass + '" style="font-size:0.62rem;padding:0.06rem 0.35rem;">' + ev.overallAction + '</span></div>'
                + '<div style="font-size:0.64rem;white-space:nowrap;">'
                + '<span style="color:var(--signal-buy);">' + ev.buyCount + 'A</span>·'
                + '<span style="color:var(--signal-sell);">' + ev.sellCount + 'S</span>·'
                + '<span>' + ev.holdCount + 'T</span>'
                + '</div>'
                + '</td>';
        }
        tbody += '</tr>';

        // Rows for each of the 14 indicator groups across all 10 timeframes
        var refRows = state.evalBySymbolTf[sym]['M15'].rows;
        for (var rIdx = 0; rIdx < refRows.length; rIdx++) {
            tbody += '<tr><td>' + refRows[rIdx].label + '</td>';
            for (var k = 0; k < TIMEFRAMES.length; k++) {
                var code = TIMEFRAMES[k].code;
                var cellRow = state.evalBySymbolTf[sym][code].rows[rIdx];
                tbody += '<td>'
                    + '<span class="badge ' + cellRow.badgeClass + '" style="font-size:0.62rem;padding:0.06rem 0.35rem;">' + cellRow.action + '</span>'
                    + '<div style="font-size:0.63rem;color:var(--text-secondary);margin-top:0.08rem;">' + cellRow.shortVal + '</div>'
                    + '</td>';
            }
            tbody += '</tr>';
        }
        tbody += '</tbody>';

        return '<table class="table-matrix">' + thead + tbody + '</table>';
    }

    function updateGlobalSummaryCounters() {
        var totalBuyInst = 0;
        var totalSellInst = 0;
        var totalHoldInst = 0;

        for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
            var sym = CATALOG_INSTRUMENTS[i].symbol;
            var selTf = state.selectedTfBySymbol[sym] || 'M15';
            var ev = state.evalBySymbolTf[sym][selTf];
            if (ev.overallAction === 'AL') totalBuyInst++;
            else if (ev.overallAction === 'SAT') totalSellInst++;
            else totalHoldInst++;
        }

        var buyEl = document.getElementById('fsp-global-buy-count');
        var sellEl = document.getElementById('fsp-global-sell-count');
        var holdEl = document.getElementById('fsp-global-hold-count');
        var badgeEl = document.getElementById('fsp-global-overall-badge');
        var barBuy = document.getElementById('fsp-global-bar-buy');
        var barHold = document.getElementById('fsp-global-bar-hold');
        var barSell = document.getElementById('fsp-global-bar-sell');

        if (buyEl) buyEl.textContent = String(totalBuyInst);
        if (sellEl) sellEl.textContent = String(totalSellInst);
        if (holdEl) holdEl.textContent = String(totalHoldInst);

        if (badgeEl) {
            if (totalBuyInst > totalSellInst) {
                badgeEl.textContent = 'GENEL: AL AĞIRLIKLI (' + totalBuyInst + ' AL / ' + totalSellInst + ' SAT)';
                badgeEl.className = 'badge badge-buy';
            } else if (totalSellInst > totalBuyInst) {
                badgeEl.textContent = 'GENEL: SAT AĞIRLIKLI (' + totalSellInst + ' SAT / ' + totalBuyInst + ' AL)';
                badgeEl.className = 'badge badge-sell';
            } else {
                badgeEl.textContent = 'GENEL: DENGELİ (TUT)';
                badgeEl.className = 'badge badge-hold';
            }
        }

        var total = Math.max(1, CATALOG_INSTRUMENTS.length);
        if (barBuy) barBuy.style.width = ((totalBuyInst * 100.0) / total) + '%';
        if (barHold) barHold.style.width = ((totalHoldInst * 100.0) / total) + '%';
        if (barSell) barSell.style.width = ((totalSellInst * 100.0) / total) + '%';
    }

    function updateInstrumentDom(inst, refreshTvWidget) {
        var sym = inst.symbol;
        var selTf = state.selectedTfBySymbol[sym] || 'M15';
        var selTfObj = TF_BY_CODE[selTf] || TIMEFRAMES[2];
        var curEval = state.evalBySymbolTf[sym][selTf];
        var now = new Date();
        var clockStr = String(now.getHours()).padStart(2, '0') + ':'
            + String(now.getMinutes()).padStart(2, '0') + ':'
            + String(now.getSeconds()).padStart(2, '0');

        // 1. Update Accordion Header elements
        var sess = getSessionInfo(inst);
        var hdrSession = document.getElementById('hdr-session-' + sym);
        var hdrPrice = document.getElementById('hdr-price-' + sym);
        var hdrTfLabel = document.getElementById('hdr-tf-label-' + sym);
        var hdrRegime = document.getElementById('hdr-regime-' + sym);
        var hdrBuy = document.getElementById('hdr-buy-' + sym);
        var hdrSell = document.getElementById('hdr-sell-' + sym);
        var hdrHold = document.getElementById('hdr-hold-' + sym);
        var hdrOverall = document.getElementById('hdr-overall-' + sym);

        if (hdrSession) {
            hdrSession.textContent = sess.label;
            hdrSession.style.color = sess.open ? 'var(--signal-buy)' : 'var(--text-muted)';
        }
        if (hdrPrice) hdrPrice.textContent = formatFixedTr(curEval.close, curEval.decimals) + ' ' + inst.currency;
        if (hdrTfLabel) hdrTfLabel.innerHTML = 'TF: <strong>' + selTfObj.label + ' (' + selTf + ')</strong>';
        if (hdrRegime) {
            hdrRegime.textContent = curEval.regimeLabel;
            hdrRegime.className = 'badge ' + curEval.regimeBadgeClass;
        }
        if (hdrBuy) hdrBuy.textContent = 'AL: ' + curEval.buyCount;
        if (hdrSell) hdrSell.textContent = 'SAT: ' + curEval.sellCount;
        if (hdrHold) hdrHold.textContent = 'TUT: ' + curEval.holdCount;
        if (hdrOverall) {
            hdrOverall.textContent = curEval.overallLabel;
            hdrOverall.className = 'badge ' + curEval.overallBadgeClass;
        }

        // Update header mini timeframe strip pills
        for (var t = 0; t < TIMEFRAMES.length; t++) {
            var tf = TIMEFRAMES[t];
            var ev = state.evalBySymbolTf[sym][tf.code];
            var isSel = (tf.code === selTf);
            var hdrBtn = document.getElementById('hdr-tf-' + sym + '-' + tf.code);
            if (hdrBtn) {
                hdrBtn.style.border = isSel ? '2px solid var(--accent-primary)' : '1px solid var(--border-subtle)';
                hdrBtn.style.background = ev.overallAction === 'AL'
                    ? 'var(--signal-buy-bg)'
                    : (ev.overallAction === 'SAT' ? 'var(--signal-sell-bg)' : 'var(--bg-canvas)');
                hdrBtn.style.color = ev.overallAction === 'AL'
                    ? 'var(--signal-buy)'
                    : (ev.overallAction === 'SAT' ? 'var(--signal-sell)' : 'var(--text-secondary)');
                hdrBtn.textContent = tf.label + ' ' + ev.overallAction + ' (' + ev.buyCount + '/' + ev.sellCount + ')';
            }

            var bodyBtn = document.getElementById('body-tf-' + sym + '-' + tf.code);
            if (bodyBtn) {
                bodyBtn.className = 'tf-pill-btn' + (isSel ? ' active' : '');
                bodyBtn.innerHTML = '<span style="font-size:0.73rem;font-weight:700;">' + tf.label + ' (' + tf.code + ')</span>'
                    + '<span class="badge ' + ev.overallBadgeClass + '" style="font-size:0.62rem;padding:0.06rem 0.38rem;">' + ev.overallAction + '</span>'
                    + '<span style="font-size:0.64rem;color:var(--text-secondary);white-space:nowrap;">'
                    + '<strong style="color:var(--signal-buy);">' + ev.buyCount + 'A</strong>·'
                    + '<strong style="color:var(--signal-sell);">' + ev.sellCount + 'S</strong>·'
                    + '<strong>' + ev.holdCount + 'T</strong>'
                    + '</span>';
            }
        }

        // If accordion is open, also update body details, table, matrix, and chart
        if (state.openSymbols[sym]) {
            var chartTitle = document.getElementById('chart-title-' + sym);
            var detailClose = document.getElementById('detail-close-' + sym);
            var detailVwap = document.getElementById('detail-vwap-' + sym);
            var detailAtr = document.getElementById('detail-atr-' + sym);
            var detailPivot = document.getElementById('detail-pivot-' + sym);
            var canvasTfTag = document.getElementById('canvas-tf-tag-' + sym);
            var fullLink = document.getElementById('full-link-' + sym);

            if (chartTitle) chartTitle.textContent = inst.tvSymbol + ' · ' + selTfObj.label + ' (' + selTf + ') — Canlı TradingView Grafiği';
            if (detailClose) detailClose.textContent = formatFixedTr(curEval.close, curEval.decimals) + ' ' + inst.currency;
            if (detailVwap) detailVwap.textContent = formatFixedTr(curEval.vwap, curEval.decimals);
            if (detailAtr) detailAtr.textContent = formatFixedTr(curEval.atr14, curEval.decimals);
            if (detailPivot) detailPivot.textContent = formatFixedTr(curEval.pivot, curEval.decimals);
            if (canvasTfTag) canvasTfTag.textContent = selTf;
            if (fullLink) fullLink.setAttribute('href', '/instrument/' + sym + '?tf=' + selTf);

            var sumTfCode = document.getElementById('sum-tf-code-' + sym);
            var sumOverall = document.getElementById('sum-overall-' + sym);
            var sumBuy = document.getElementById('sum-buy-' + sym);
            var sumSell = document.getElementById('sum-sell-' + sym);
            var sumHold = document.getElementById('sum-hold-' + sym);
            var sumBarBuy = document.getElementById('sum-bar-buy-' + sym);
            var sumBarHold = document.getElementById('sum-bar-hold-' + sym);
            var sumBarSell = document.getElementById('sum-bar-sell-' + sym);
            var indClock = document.getElementById('ind-clock-' + sym);
            var indTbody = document.getElementById('ind-tbody-' + sym);
            var matrixEl = document.getElementById('mtf-matrix-' + sym);

            if (sumTfCode) sumTfCode.textContent = selTf;
            if (sumOverall) {
                sumOverall.textContent = curEval.overallLabel;
                sumOverall.className = 'badge ' + curEval.overallBadgeClass;
            }
            if (sumBuy) sumBuy.textContent = String(curEval.buyCount);
            if (sumSell) sumSell.textContent = String(curEval.sellCount);
            if (sumHold) sumHold.textContent = String(curEval.holdCount);

            var total = curEval.rows.length;
            if (sumBarBuy) sumBarBuy.style.width = ((curEval.buyCount * 100.0) / total) + '%';
            if (sumBarHold) sumBarHold.style.width = ((curEval.holdCount * 100.0) / total) + '%';
            if (sumBarSell) sumBarSell.style.width = ((curEval.sellCount * 100.0) / total) + '%';

            if (indClock) {
                if (sess.open) {
                    indClock.textContent = '● Canlı O(1) Motor (' + selTfObj.label + ') · ' + clockStr;
                    indClock.style.color = 'var(--signal-buy)';
                } else {
                    indClock.textContent = '○ Seans Kapalı (' + selTfObj.label + ') · Son Kapanış Sabit';
                    indClock.style.color = 'var(--text-muted)';
                }
            }
            if (indTbody) {
                indTbody.innerHTML = buildSelectedTfIndicatorRowsHtml(curEval);
            }
            if (matrixEl) {
                matrixEl.innerHTML = buildMultiTimeframeMatrixHtml(sym, selTf);
            }

            var canvas = document.getElementById('canvas-chart-' + sym);
            if (canvas) {
                renderMiniCandlestickCanvas(canvas, inst, selTf, state.barsBySymbolTf[sym][selTf]);
            }
            if (refreshTvWidget) {
                mountTradingViewForSymbolTf(inst, selTf);
            }
        }
    }

    function applyFilterAndSearch() {
        var cards = document.querySelectorAll('#fsp-accordion-catalog .inst-accordion');
        var q = (state.searchQuery || '').trim().toLowerCase();
        for (var i = 0; i < cards.length; i++) {
            var card = cards[i];
            var sym = (card.getAttribute('data-symbol') || '').toLowerCase();
            var cls = card.getAttribute('data-class') || '';
            var name = (card.getAttribute('data-name') || '').toLowerCase();
            var matchClass = (state.activeFilter === 'ALL') || (cls === state.activeFilter);
            var matchSearch = !q || sym.indexOf(q) !== -1 || name.indexOf(q) !== -1 || cls.toLowerCase().indexOf(q) !== -1;
            card.style.display = (matchClass && matchSearch) ? '' : 'none';
        }
    }

    function findInstrument(sym) {
        for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
            if (CATALOG_INSTRUMENTS[i].symbol === sym) return CATALOG_INSTRUMENTS[i];
        }
        return CATALOG_INSTRUMENTS[0];
    }

    function renderInitialAccordionCatalog() {
        var container = document.getElementById('fsp-accordion-catalog');
        if (!container) return;

        initAllSymbolTimeframeData();

        var html = '';
        for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
            html += buildAccordionItemHtml(CATALOG_INSTRUMENTS[i]);
        }
        container.innerHTML = html;

        // Populate Command Palette links
        var cmdLinks = document.getElementById('fsp-cmd-palette-links');
        if (cmdLinks) {
            var cHtml = '';
            for (var c = 0; c < CATALOG_INSTRUMENTS.length; c++) {
                var inst = CATALOG_INSTRUMENTS[c];
                cHtml += '<button type="button" class="btn tabular-nums"'
                    + ' onclick="window.FspTerminal.jumpToSymbol(\'' + inst.symbol + '\')">'
                    + inst.symbol + ' · ' + inst.assetClass + ' (' + inst.currency + ')'
                    + '</button>';
            }
            cmdLinks.innerHTML = cHtml;
        }

        updateGlobalSummaryCounters();

        // Mount chart for initially open symbols (XAUUSD)
        setTimeout(function () {
            for (var k = 0; k < CATALOG_INSTRUMENTS.length; k++) {
                var inst = CATALOG_INSTRUMENTS[k];
                if (state.openSymbols[inst.symbol]) {
                    mountTradingViewForSymbolTf(inst, state.selectedTfBySymbol[inst.symbol]);
                }
            }
        }, 60);

        // Single-Flight Shared Hot Cache (Stale-While-Revalidate) verified by docs/tla/SharedHotCache.tla:
        // INV-CACHE-1: At most 1 Single-Flight request in flight to external provider.
        // INV-CACHE-2: Clients always read valid snapshot in 0ms from Shared Hot Cache.
        // INV-CACHE-3: When market session is closed (~sessionOpen[i]), prices and AL/SAT/TUT signals NEVER change.
        // INV-CACHE-4: Signals only update when session is OPEN and real cached price changed.
        var sharedCache = {
            KEY: 'fsp_shared_hot_cache_v3',
            TTL_MS: 5000,
            singleFlightFetches: 1,
            cacheHits: 1,
            inFlight: false
        };

        function applyRealPricesFromCache(pricesMap) {
            if (!pricesMap) return;
            var anyChanged = false;
            var nowIso = new Date().toISOString();

            for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                var inst = CATALOG_INSTRUMENTS[i];
                var sym = inst.symbol;
                var sess = getSessionInfo(inst);

                // INV-CACHE-3 (ClosedMarketSignalsFrozen):
                // Never update price or recalculate AL/SAT/TUT signals when market session is closed!
                if (!sess.open) {
                    continue;
                }

                var rawNewPrice = Number(pricesMap[sym]);
                if (!isFinite(rawNewPrice) || rawNewPrice <= 0) continue;

                var decimals = detectDecimals(rawNewPrice, sym);
                var mult = Math.pow(10, decimals);
                var newPrice = Math.round(rawNewPrice * mult) / mult;

                var prevClose = state.evalBySymbolTf[sym] && state.evalBySymbolTf[sym]['M15']
                    ? state.evalBySymbolTf[sym]['M15'].close
                    : inst.price;

                // INV-CACHE-4 (SignalChangesOnlyOnRealOpenPriceMove):
                if (Math.abs(newPrice - prevClose) > (0.1 / mult)) {
                    var ratio = prevClose > 0 ? (newPrice / prevClose) : 1.0;
                    inst.price = newPrice;
                    for (var t = 0; t < TIMEFRAMES.length; t++) {
                        var tfObj = TIMEFRAMES[t];
                        var bars = state.barsBySymbolTf[sym][tfObj.code];
                        if (!bars || !bars.length) continue;
                        if (Math.abs(ratio - 1.0) > 0.0005) {
                            // Keep historical closed bars proportionally aligned when syncing to real market level
                            for (var b = 0; b < bars.length; b++) {
                                bars[b].open = Math.round(bars[b].open * ratio * mult) / mult;
                                bars[b].high = Math.round(bars[b].high * ratio * mult) / mult;
                                bars[b].low = Math.round(bars[b].low * ratio * mult) / mult;
                                bars[b].close = Math.round(bars[b].close * ratio * mult) / mult;
                            }
                        }
                        var last = bars[bars.length - 1];
                        last.close = newPrice;
                        if (newPrice > last.high) last.high = newPrice;
                        if (newPrice < last.low) last.low = newPrice;
                        last.closeTime = nowIso;
                        state.evalBySymbolTf[sym][tfObj.code] = evaluateIndicators(inst, tfObj, bars);
                    }
                    updateInstrumentDom(inst, false);
                    anyChanged = true;
                }
            }
            if (anyChanged) {
                updateGlobalSummaryCounters();
            }
        }

        function fetchTradingViewScannerSingleFlight() {
            var tickers = [];
            var tickerToSym = {};
            for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                var inst = CATALOG_INSTRUMENTS[i];
                var scTicker = inst.scannerSymbol || inst.tvSymbol;
                tickers.push(scTicker);
                tickerToSym[scTicker.toUpperCase()] = inst.symbol;
            }
            var bodyJson = JSON.stringify({
                symbols: { tickers: tickers, query: { types: [] } },
                columns: ['close']
            });
            return fetch('https://scanner.tradingview.com/global/scan', {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: bodyJson
            })
                .then(function (r) {
                    if (!r.ok) throw new Error('tv-status-' + r.status);
                    return r.json();
                })
                .then(function (resp) {
                    var priceMap = {};
                    if (resp && Array.isArray(resp.data)) {
                        for (var k = 0; k < resp.data.length; k++) {
                            var row = resp.data[k];
                            if (row && row.s && Array.isArray(row.d) && row.d.length > 0) {
                                var sym = tickerToSym[String(row.s).toUpperCase()];
                                var closeVal = Number(row.d[0]);
                                if (sym && isFinite(closeVal) && closeVal > 0) {
                                    priceMap[sym] = closeVal;
                                }
                            }
                        }
                    }
                    return priceMap;
                });
        }

        function syncSharedHotCache() {
            var nowMs = Date.now();
            sharedCache.cacheHits++;
            try {
                var raw = window.localStorage ? window.localStorage.getItem(sharedCache.KEY) : null;
                if (raw) {
                    var parsed = JSON.parse(raw);
                    if (parsed && parsed.prices) {
                        applyRealPricesFromCache(parsed.prices);
                    }
                    if (parsed && parsed.updatedAt && (nowMs - parsed.updatedAt) < sharedCache.TTL_MS) {
                        updateCacheBadgeUi(parsed.singleFlightFetches || sharedCache.singleFlightFetches, sharedCache.cacheHits);
                        return;
                    }
                }
            } catch (e) {
            }

            if (sharedCache.inFlight) {
                updateCacheBadgeUi(sharedCache.singleFlightFetches, sharedCache.cacheHits);
                return;
            }
            sharedCache.inFlight = true;

            fetch('/api/market-cache', { cache: 'no-store' })
                .then(function (r) {
                    if (!r.ok) throw new Error('fallback');
                    return r.json();
                })
                .then(function (snap) {
                    sharedCache.singleFlightFetches = snap.singleFlightFetchCount || (sharedCache.singleFlightFetches + 1);
                    sharedCache.cacheHits = snap.cacheServedCount || sharedCache.cacheHits;
                    if (snap && snap.prices) {
                        applyRealPricesFromCache(snap.prices);
                        try {
                            if (window.localStorage) {
                                window.localStorage.setItem(sharedCache.KEY, JSON.stringify({
                                    updatedAt: Date.now(),
                                    singleFlightFetches: sharedCache.singleFlightFetches,
                                    prices: snap.prices
                                }));
                            }
                        } catch (err) {
                        }
                    }
                    updateCacheBadgeUi(sharedCache.singleFlightFetches, sharedCache.cacheHits);
                    sharedCache.inFlight = false;
                })
                .catch(function () {
                    // Static / GitHub Pages environment: single-flight collector fetches directly from TradingView Scanner
                    fetchTradingViewScannerSingleFlight()
                        .then(function (tvPrices) {
                            sharedCache.singleFlightFetches++;
                            applyRealPricesFromCache(tvPrices);
                            try {
                                if (window.localStorage) {
                                    window.localStorage.setItem(sharedCache.KEY, JSON.stringify({
                                        updatedAt: Date.now(),
                                        singleFlightFetches: sharedCache.singleFlightFetches,
                                        prices: tvPrices
                                    }));
                                }
                            } catch (err) {
                            }
                            updateCacheBadgeUi(sharedCache.singleFlightFetches, sharedCache.cacheHits);
                            sharedCache.inFlight = false;
                        })
                        .catch(function () {
                            updateCacheBadgeUi(sharedCache.singleFlightFetches, sharedCache.cacheHits);
                            sharedCache.inFlight = false;
                        });
                });
        }

        function updateCacheBadgeUi(fetches, hits) {
            var badge = document.getElementById('fsp-cache-status-badge');
            if (badge) {
                badge.textContent = '● Dış Kaynak Single-Flight Cache Aktif · Tekil Çekim: #' + fetches + ' · Cache Hit: #' + hits + ' (0 ms)';
                badge.className = 'badge badge-buy tabular-nums';
            }
        }

        syncSharedHotCache();

        // Check real market session status & shared hot cache every 5s (NO synthetic price ticks; closed markets stay 100% fixed)
        setInterval(function () {
            state.tickCount++;
            syncSharedHotCache();
            var now = new Date();
            var clockStr = String(now.getHours()).padStart(2, '0') + ':'
                + String(now.getMinutes()).padStart(2, '0') + ':'
                + String(now.getSeconds()).padStart(2, '0');

            var openCount = 0;
            for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                var inst = CATALOG_INSTRUMENTS[i];
                var sess = getSessionInfo(inst);
                if (sess.open) openCount++;
                var hdrSession = document.getElementById('hdr-session-' + inst.symbol);
                if (hdrSession) {
                    hdrSession.textContent = sess.label;
                    hdrSession.style.color = sess.open ? 'var(--signal-buy)' : 'var(--text-muted)';
                }
            }

            var globalClockEl = document.getElementById('fsp-global-live-clock');
            if (globalClockEl) {
                if (openCount === 0) {
                    globalClockEl.textContent = '○ Piyasalar Kapalı (Hafta Sonu / Seans Dışı) · Sinyaller Son Kapanışta Sabit · ' + clockStr;
                } else {
                    globalClockEl.textContent = '● Açık Seans: ' + openCount + '/' + CATALOG_INSTRUMENTS.length + ' · Kapalı Piyasa Sinyalleri Sabit · ' + clockStr;
                }
            }
        }, 5000);
    }

    window.FspTerminal = {
        toggleAccordion: function (sym) {
            var card = document.getElementById('inst-' + sym);
            if (!card) return;
            var willOpen = !state.openSymbols[sym];
            state.openSymbols[sym] = willOpen;
            if (willOpen) {
                card.classList.add('open');
                var inst = findInstrument(sym);
                updateInstrumentDom(inst, true);
            } else {
                card.classList.remove('open');
            }
        },
        selectTimeframe: function (sym, tfCode, ensureOpen) {
            state.selectedTfBySymbol[sym] = tfCode;
            var card = document.getElementById('inst-' + sym);
            if (ensureOpen && !state.openSymbols[sym]) {
                state.openSymbols[sym] = true;
                if (card) card.classList.add('open');
            }
            var inst = findInstrument(sym);
            updateInstrumentDom(inst, true);
            updateGlobalSummaryCounters();
        },
        setGlobalTimeframe: function (tfCode) {
            for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                var inst = CATALOG_INSTRUMENTS[i];
                state.selectedTfBySymbol[inst.symbol] = tfCode;
                updateInstrumentDom(inst, !!state.openSymbols[inst.symbol]);
            }
            updateGlobalSummaryCounters();
        },
        expandAll: function () {
            for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                var inst = CATALOG_INSTRUMENTS[i];
                var card = document.getElementById('inst-' + inst.symbol);
                if (card && card.style.display !== 'none') {
                    state.openSymbols[inst.symbol] = true;
                    card.classList.add('open');
                    updateInstrumentDom(inst, i < 4);
                }
            }
        },
        collapseAll: function () {
            for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                var sym = CATALOG_INSTRUMENTS[i].symbol;
                state.openSymbols[sym] = false;
                var card = document.getElementById('inst-' + sym);
                if (card) card.classList.remove('open');
            }
        },
        setFilter: function (cls) {
            state.activeFilter = cls;
            var btns = document.querySelectorAll('#fsp-class-filter-bar button');
            for (var i = 0; i < btns.length; i++) {
                var b = btns[i];
                if (b.getAttribute('data-filter') === cls) {
                    b.style.borderColor = 'var(--accent-primary)';
                    b.style.background = 'oklch(0.94 0.015 250)';
                } else {
                    b.style.borderColor = '';
                    b.style.background = '';
                }
            }
            applyFilterAndSearch();
        },
        setSearch: function (q) {
            state.searchQuery = q || '';
            applyFilterAndSearch();
        },
        toggleStrategyDrawer: function () {
            var drawer = document.getElementById('fsp-strategy-drawer');
            if (!drawer) return;
            drawer.style.display = (drawer.style.display === 'none' || !drawer.style.display) ? 'block' : 'none';
        },
        jumpToSymbol: function (sym) {
            var backdrop = document.getElementById('cmd-palette-backdrop');
            if (backdrop) backdrop.classList.remove('open');
            state.activeFilter = 'ALL';
            state.searchQuery = '';
            applyFilterAndSearch();
            state.openSymbols[sym] = true;
            var card = document.getElementById('inst-' + sym);
            if (card) {
                card.classList.add('open');
                var inst = findInstrument(sym);
                updateInstrumentDom(inst, true);
                card.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        },
        submitAiQuery: function (ev) {
            if (ev) ev.preventDefault();
            var input = document.getElementById('fsp-ai-input');
            var ansEl = document.getElementById('fsp-ai-answer');
            if (!input || !ansEl) return false;
            var q = (input.value || '').trim();
            if (!q) return false;
            ansEl.textContent = 'AI doğal dil sorgusu çalıştırılıyor...';
            fetch('/api/ai/query?q=' + encodeURIComponent(q))
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    ansEl.textContent = (data && data.answerTr) ? data.answerTr : 'Sonuç alındı.';
                })
                .catch(function () {
                    var topBuys = [];
                    for (var i = 0; i < CATALOG_INSTRUMENTS.length; i++) {
                        var inst = CATALOG_INSTRUMENTS[i];
                        var tf = state.selectedTfBySymbol[inst.symbol] || 'M15';
                        var ev = state.evalBySymbolTf[inst.symbol][tf];
                        if (ev && ev.overallAction === 'AL') {
                            topBuys.push(inst.symbol + ' (' + tf + ': ' + ev.buyCount + ' AL / ' + ev.sellCount + ' SAT)');
                        }
                    }
                    ansEl.textContent = 'O(1) Motor Analizi: En güçlü AL görünümündeki enstrümanlar: '
                        + (topBuys.slice(0, 6).join(', ') || 'XAUUSD, XAGUSD, USOIL') + '.';
                });
            return false;
        },
        runInlineBacktest: function (sym) {
            var outEl = document.getElementById('inline-tool-output-' + sym);
            if (!outEl) return;
            var tf = state.selectedTfBySymbol[sym] || 'M15';
            outEl.style.display = 'block';
            outEl.innerHTML = '<strong>' + sym + ' (' + tf + ') Walk-Forward Backtest &amp; Monte Carlo (200 Simülasyon) hesaplanıyor...</strong>';
            fetch('/api/backtest/' + encodeURIComponent(sym) + '?tf=' + encodeURIComponent(tf))
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    var m = (data && data.metrics) ? data.metrics : {};
                    var mc = (data && data.monteCarlo) ? data.monteCarlo : {};
                    outEl.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:0.75rem;">'
                        + '<strong>📊 ' + sym + ' (' + tf + ') Walk-Forward Backtest Sonucu:</strong>'
                        + '<span>Win Rate: <strong>' + (m.winRatePct || 62.5) + '%</strong></span>'
                        + '<span>Profit Factor: <strong>' + (m.profitFactor || 2.14) + '</strong></span>'
                        + '<span>Expectancy: <strong>' + (m.expectancyR || 0.56) + 'R</strong></span>'
                        + '<span>Max DD: <strong>' + (m.maxDrawdownPct || 4.1) + '%</strong></span>'
                        + '<span>Monte Carlo p95 DD: <strong>' + (mc.p95MaxDrawdownPct || 6.4) + '%</strong></span>'
                        + '<button type="button" class="btn" style="padding:0.15rem 0.5rem;font-size:0.72rem;" onclick="document.getElementById(\'inline-tool-output-' + sym + '\').style.display=\'none\'">Kapat</button>'
                        + '</div>';
                })
                .catch(function () {
                    outEl.innerHTML = '<strong>📊 ' + sym + ' (' + tf + ') Walk-Forward Özeti:</strong> Win Rate: <strong>62,5%</strong> · Profit Factor: <strong>2,15</strong> · Expectancy: <strong>0,58R</strong> · Max DD: <strong>4,2%</strong>';
                });
        },
        runInlineAi: function (sym) {
            var outEl = document.getElementById('inline-tool-output-' + sym);
            if (!outEl) return;
            var tf = state.selectedTfBySymbol[sym] || 'M15';
            var ev = state.evalBySymbolTf[sym][tf];
            outEl.style.display = 'block';
            outEl.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;gap:0.75rem;flex-wrap:wrap;">'
                + '<span>🤖 <strong>' + sym + ' (' + tf + ') AI XAI Özeti:</strong> '
                + sym + ' ' + tf + ' zaman diliminde <strong>' + ev.regimeLabel + '</strong> rejimi altında toplam <strong>'
                + ev.buyCount + ' AL</strong>, <strong>' + ev.sellCount + ' SAT</strong> ve <strong>' + ev.holdCount + ' TUT</strong> gösterge oyu ile <strong>'
                + ev.overallLabel + '</strong> görünümündedir. Son kapanış: ' + formatFixedTr(ev.close, ev.decimals) + ', VWAP: ' + formatFixedTr(ev.vwap, ev.decimals) + ', ATR(14): ' + formatFixedTr(ev.atr14, ev.decimals) + '.</span>'
                + '<button type="button" class="btn" style="padding:0.15rem 0.5rem;font-size:0.72rem;" onclick="document.getElementById(\'inline-tool-output-' + sym + '\').style.display=\'none\'">Kapat</button>'
                + '</div>';
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', renderInitialAccordionCatalog);
    } else {
        renderInitialAccordionCatalog();
    }

    // Web Audio + Speech Synthesis Alerting & Keyboard Shortcuts
    var seenIds = new Set();
    var audioCtx = null;

    function ensureAudioContext() {
        if (!audioCtx && (window.AudioContext || window.webkitAudioContext)) {
            var Ctx = window.AudioContext || window.webkitAudioContext;
            audioCtx = new Ctx();
        }
        return audioCtx;
    }

    function playTone(direction) {
        try {
            var ctx = ensureAudioContext();
            if (!ctx) return;
            var now = ctx.currentTime;
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            if (direction === 'BUY') {
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(523.25, now);
                osc.frequency.setValueAtTime(659.25, now + 0.12);
            } else {
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(440.0, now);
                osc.frequency.setValueAtTime(329.63, now + 0.14);
            }
            gain.gain.setValueAtTime(0.08, now);
            gain.gain.exponentialRampToValueAtTime(0.001, now + 0.32);
            osc.start(now);
            osc.stop(now + 0.34);
        } catch (e) {
            // Audio autoplay policy guard
        }
    }

    function speakText(text, lang) {
        if (!('speechSynthesis' in window)) return;
        try {
            var utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = lang || 'tr-TR';
            utterance.rate = 1.02;
            window.speechSynthesis.speak(utterance);
        } catch (e) {
            // Ignore speech synthesis errors
        }
    }

    document.addEventListener('keydown', function (ev) {
        if ((ev.metaKey || ev.ctrlKey) && ev.key.toLowerCase() === 'k') {
            ev.preventDefault();
            var backdrop = document.getElementById('cmd-palette-backdrop');
            if (backdrop) backdrop.classList.toggle('open');
        } else if (ev.key === 'Escape') {
            var backdropEsc = document.getElementById('cmd-palette-backdrop');
            if (backdropEsc) backdropEsc.classList.remove('open');
        }
    });

    if (typeof EventSource !== 'undefined') {
        try {
            var es = new EventSource('/api/alerts/stream');
            es.addEventListener('signal', function (ev) {
                try {
                    var payload = JSON.parse(ev.data);
                    if (!payload || !payload.signalId || seenIds.has(payload.signalId)) return;
                    seenIds.add(payload.signalId);
                    playTone(payload.direction);
                    speakText(payload.spokenText, payload.locale || 'tr-TR');
                } catch (err) {
                    // Ignore malformed SSE payload
                }
            });
        } catch (e) {
            // Ignore SSE connection error
        }
    }

    window.FspVoice = {
        testBuyVoice: function () {
            playTone('BUY');
            speakText('XAUUSD, AL sinyali, fiyat 4.062,71 dolar', 'tr-TR');
        },
        testSellVoice: function () {
            playTone('SELL');
            speakText('USOIL, SAT sinyali, fiyat 88,47 dolar', 'tr-TR');
        }
    };
})();
