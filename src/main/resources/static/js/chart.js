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

    function resolveTradingViewSymbol(meta) {
        var sym = (meta && meta.symbol) ? meta.symbol : 'XAUUSD';
        var ex = (meta && meta.exchange) ? meta.exchange : 'OANDA';
        if (sym === 'SPX500') return 'FOREXCOM:SPXUSD';
        if (sym === 'NDX100') return 'NASDAQ:NDX';
        return ex + ':' + sym;
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
        var baseClose = (meta && meta.close && meta.close > 0)
            ? meta.close
            : (rawBars.length > 0 ? rawBars[rawBars.length - 1].close : 100.0);
        var sym = (meta && meta.symbol) ? meta.symbol : 'XAUUSD';
        var decimals = detectDecimals(baseClose, sym);
        var mult = Math.pow(10, decimals);

        var tfSeed = 0;
        for (var s = 0; s < sym.length; s++) tfSeed += sym.charCodeAt(s) * (s + 3);
        for (var c = 0; c < tf.length; c++) tfSeed += tf.charCodeAt(c) * (c + 11) * 17;

        var nowAligned = Math.floor(Date.now() / stepMs) * stepMs;
        var generated = [];

        function rawFactorAt(idx) {
            var w = (Math.sin((idx + tfSeed) * 0.19) * 0.008 + Math.cos((idx + tfSeed) * 0.06) * 0.012) * amp;
            var d = ((idx - 64) * 0.00025 * amp) * ((tfSeed % 2 === 0) ? 1.0 : -0.6);
            var oF = Math.max(0.1, 1.0 + w + d);
            var dF = Math.sin((idx + tfSeed) * 0.39) * 0.0045 * amp;
            return Math.max(0.1, oF + dF);
        }

        var ref64 = rawFactorAt(64);
        var scale = baseClose / ref64;

        for (var i = 0; i < 65; i++) {
            var w = (Math.sin((i + tfSeed) * 0.19) * 0.008 + Math.cos((i + tfSeed) * 0.06) * 0.012) * amp;
            var d = ((i - 64) * 0.00025 * amp) * ((tfSeed % 2 === 0) ? 1.0 : -0.6);
            var oF = Math.max(0.1, 1.0 + w + d);
            var dF = Math.sin((i + tfSeed) * 0.39) * 0.0045 * amp;
            var cF = Math.max(0.1, oF + dF);
            var hF = Math.max(oF, cF) + Math.abs(dF) * 0.65 + 0.0012 * amp;
            var lF = Math.max(0.05, Math.min(oF, cF) - Math.abs(dF) * 0.55 - 0.0010 * amp);

            var open = Math.round(oF * scale * mult) / mult;
            var close = (i === 64) ? Math.round(baseClose * mult) / mult : Math.round(cF * scale * mult) / mult;
            var high = Math.max(open, close, Math.round(hF * scale * mult) / mult);
            var low = Math.min(open, close, Math.round(lF * scale * mult) / mult);
            var vol = Math.round((10000 + ((i * 37 + tfSeed * 101) % 15000) * ((i % 7 === 0) ? 1.85 : 1.0)) * amp);
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

    function initTradingViewPrimaryChart(meta, bars) {
        var container = document.getElementById('tradingview_advanced_chart');
        if (!container) return;

        var tvSymbol = resolveTradingViewSymbol(meta);
        var tvInterval = resolveTradingViewInterval(meta.timeframe || 'M15');

        // 1. Official TradingView Advanced Real-Time Widget (tv.js)
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
            return;
        }

        // 2. Official TradingView Lightweight Charts (unpkg.com)
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

            var ema9Series = chart.addLineSeries({
                color: '#1862ff',
                lineWidth: 2,
                title: 'EMA 9'
            });

            var ema21Series = chart.addLineSeries({
                color: '#d97706',
                lineWidth: 2,
                title: 'EMA 21'
            });

            var ema50Series = chart.addLineSeries({
                color: '#7c3aed',
                lineWidth: 2,
                title: 'EMA 50'
            });

            var volumeSeries = chart.addHistogramSeries({
                color: '#94a3b8',
                priceFormat: { type: 'volume' },
                priceScaleId: ''
            });
            volumeSeries.priceScale().applyOptions({
                scaleMargins: { top: 0.8, bottom: 0 }
            });

            var ema9Vals = computeEmaSeries(bars, 9);
            var ema21Vals = computeEmaSeries(bars, 21);
            var ema50Vals = computeEmaSeries(bars, 50);

            var candleData = [];
            var volData = [];
            var e9Data = [];
            var e21Data = [];
            var e50Data = [];

            for (var i = 0; i < bars.length; i++) {
                var b = bars[i];
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
            chart.timeScale().fitContent();

            window.addEventListener('resize', function () {
                if (container.clientWidth > 0) {
                    chart.applyOptions({ width: container.clientWidth });
                }
            });
        }
    }

    function initFspCanvasChart(meta, rawBars) {
        var canvas = document.getElementById('fsp-candles-canvas');
        if (!canvas || !rawBars || rawBars.length === 0) return;

        var tf = (meta && meta.timeframe) ? meta.timeframe : 'M15';
        var symbol = meta.symbol || 'XAUUSD';
        var currency = meta.currency || 'USD';
        var decimals = detectDecimals(rawBars[rawBars.length - 1].close, symbol);

        var ema9 = computeEmaSeries(rawBars, 9);
        var ema21 = computeEmaSeries(rawBars, 21);
        var ema50 = computeEmaSeries(rawBars, 50);

        var hoverIndex = -1;
        var hoverY = -1;
        var ohlcEl = document.getElementById('fsp-ohlc-readout');

        function updateLegend(idx) {
            if (!ohlcEl) return;
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
    }

    function boot() {
        var meta = window.FSP_CHART_META || {};
        var bars = prepareTimeframeBars(meta);
        initTradingViewPrimaryChart(meta, bars);
        initFspCanvasChart(meta, bars);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
