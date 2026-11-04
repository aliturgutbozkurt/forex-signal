(function () {
    'use strict';

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
        var hh = String(d.getUTCHours()).padStart(2, '0');
        var mm = String(d.getUTCMinutes()).padStart(2, '0');
        if (tf === 'D1') {
            return day + '.' + mon;
        }
        return day + '.' + mon + ' ' + hh + ':' + mm;
    }

    function initChart() {
        var canvas = document.getElementById('fsp-candles-canvas');
        if (!canvas) return;

        var rawBars = window.FSP_BARS_DATA || [];
        var meta = window.FSP_CHART_META || {};
        if (!rawBars || rawBars.length === 0) {
            var baseClose = meta.close && meta.close > 0 ? meta.close : 100.0;
            rawBars = [];
            var nowMs = Date.now();
            var stepMs = meta.timeframe === 'D1' ? 86400000 : (meta.timeframe === 'H4' ? 14400000 : (meta.timeframe === 'H1' ? 3600000 : 900000));
            for (var fb = 0; fb < 65; fb++) {
                var w = Math.sin((fb + 17) * 0.18) * 0.012 + Math.cos((fb + 17) * 0.05) * 0.018;
                var d = ((fb - 64) * 0.0005);
                var o = baseClose * (1.0 + w + d - Math.sin((64 + 17) * 0.18) * 0.012 - Math.cos((64 + 17) * 0.05) * 0.018);
                var dl = Math.sin((fb + 17) * 0.37) * (baseClose * 0.004);
                var c = fb === 64 ? baseClose : (o + dl);
                var h = Math.max(o, c) + Math.abs(dl) * 0.65 + baseClose * 0.0012;
                var l = Math.min(o, c) - Math.abs(dl) * 0.55 - baseClose * 0.0010;
                var ct = new Date(nowMs - (64 - fb) * stepMs).toISOString();
                rawBars.push({ barIndex: fb, openTime: ct, closeTime: ct, open: o, high: h, low: l, close: c, volume: 12000 + ((fb * 731) % 9000) });
            }
        }

        var symbol = meta.symbol || 'XAUUSD';
        var tf = meta.timeframe || 'M15';
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
                '<span style="color:#525866;margin-right:0.65rem;">' + formatTimeLabel(b.closeTime, tf) + '</span>' +
                '<span style="margin-right:0.65rem;">A: <strong>' + formatPrice(b.open, decimals) + '</strong></span>' +
                '<span style="margin-right:0.65rem;">Y: <strong>' + formatPrice(b.high, decimals) + '</strong></span>' +
                '<span style="margin-right:0.65rem;">D: <strong>' + formatPrice(b.low, decimals) + '</strong></span>' +
                '<span style="margin-right:0.65rem;">K: <strong style="color:' + color + ';">' + formatPrice(b.close, decimals) + ' ' + currency + ' (' + sign + chg + '%)</strong></span>' +
                '<span style="margin-right:0.65rem;">Hacim: <strong>' + formatVolume(b.volume) + '</strong></span>' +
                '<span style="color:#1862ff;margin-right:0.5rem;">EMA9: ' + formatPrice(ema9[i], decimals) + '</span>' +
                '<span style="color:#d97706;margin-right:0.5rem;">EMA21: ' + formatPrice(ema21[i], decimals) + '</span>' +
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
            var height = Math.max(280, Math.floor(rect.height));

            if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
                canvas.width = width * dpr;
                canvas.height = height * dpr;
            }

            var ctx = canvas.getContext('2d');
            ctx.save();
            ctx.scale(dpr, dpr);

            // Background
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, width, height);

            var rightAxisWidth = 84;
            var padLeft = 12;
            var padTop = 16;
            var timeAxisHeight = 26;
            var chartWidth = width - padLeft - rightAxisWidth;
            var totalPlotHeight = height - padTop - timeAxisHeight;
            var pricePlotHeight = Math.floor(totalPlotHeight * 0.78);
            var volPlotTop = padTop + pricePlotHeight + 8;
            var volPlotHeight = totalPlotHeight - pricePlotHeight - 8;
            var rightAxisX = width - rightAxisWidth;

            // Compute price min/max
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

            if (meta.pivot && meta.pivot > 0) {
                minPrice = Math.min(minPrice, meta.pivot);
                maxPrice = Math.max(maxPrice, meta.pivot);
            }
            if (meta.vwap && meta.vwap > 0) {
                minPrice = Math.min(minPrice, meta.vwap);
                maxPrice = Math.max(maxPrice, meta.vwap);
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

            // Grid lines & right price labels
            ctx.strokeStyle = '#eef0f4';
            ctx.lineWidth = 1;
            ctx.font = '11px ui-monospace, SFMono-Regular, Menlo, monospace';
            ctx.fillStyle = '#525866';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';

            var gridSteps = 6;
            for (var g = 0; g <= gridSteps; g++) {
                var gy = padTop + (pricePlotHeight / gridSteps) * g;
                var gp = maxPrice - (span / gridSteps) * g;
                ctx.beginPath();
                ctx.moveTo(padLeft, gy);
                ctx.lineTo(rightAxisX, gy);
                ctx.stroke();
                ctx.fillText(formatPrice(gp, decimals), rightAxisX + 8, gy);
            }

            // Separator between price & volume and right axis border
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

            // Vertical grid + Time labels
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

            // Volume bars & Candlesticks
            for (var j = 0; j < n; j++) {
                var bar = rawBars[j];
                var cx = indexToX(j);
                var isBull = bar.close >= bar.open;
                var candleColor = isBull ? '#0d8a52' : '#c92a2a';
                var volColor = isBull ? 'rgba(13, 138, 82, 0.25)' : 'rgba(201, 42, 42, 0.25)';

                // Volume bar
                var vh = (bar.volume / maxVol) * volPlotHeight;
                ctx.fillStyle = volColor;
                ctx.fillRect(cx - bodyWidth / 2, volPlotTop + volPlotHeight - vh, bodyWidth, vh);

                // High-Low Wick
                var highY = priceToY(bar.high);
                var lowY = priceToY(bar.low);
                ctx.strokeStyle = candleColor;
                ctx.lineWidth = 1.25;
                ctx.beginPath();
                ctx.moveTo(cx, highY);
                ctx.lineTo(cx, lowY);
                ctx.stroke();

                // Open-Close Body
                var openY = priceToY(bar.open);
                var closeY = priceToY(bar.close);
                var topY = Math.min(openY, closeY);
                var bHeight = Math.max(1.5, Math.abs(closeY - openY));
                ctx.fillStyle = candleColor;
                ctx.fillRect(cx - bodyWidth / 2, topY, bodyWidth, bHeight);
            }

            // Helper to draw EMA line
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

            // Pivot & VWAP horizontal reference lines
            if (meta.pivot && meta.pivot >= minPrice && meta.pivot <= maxPrice) {
                var py = priceToY(meta.pivot);
                ctx.save();
                ctx.setLineDash([4, 4]);
                ctx.strokeStyle = '#64748b';
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(padLeft, py);
                ctx.lineTo(rightAxisX, py);
                ctx.stroke();
                ctx.restore();
                drawPriceTag(ctx, py, 'P ' + formatPrice(meta.pivot, decimals), '#e2e8f0', '#334155', rightAxisX, rightAxisWidth);
            }

            // Latest close horizontal dashed line + tag
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

            // Crosshair
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

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initChart);
    } else {
        initChart();
    }
})();
