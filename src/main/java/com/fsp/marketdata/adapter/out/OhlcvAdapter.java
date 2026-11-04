package com.fsp.marketdata.adapter.out;

import com.fsp.marketdata.application.MarketDataPort;
import com.fsp.marketdata.domain.Bar;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * OHLCV secondary adapter supporting live TradingView price anchoring, deterministic historical
 * bar generation, and gap backfill while strictly guaranteeing INV-BAR-1 (forming bars are never emitted).
 */
@NullMarked
@SecondaryAdapter
@Component
public class OhlcvAdapter implements MarketDataPort {

    private static final Instant EPOCH_ANCHOR = Instant.parse("2026-11-01T00:00:00Z");
    private static final long REFERENCE_LATEST_BAR_INDEX = 64L;
    private static final Pattern TV_ROW_PATTERN = Pattern.compile(
            "\"s\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"d\"\\s*:\\s*\\[\\s*(-?[0-9]+(?:\\.[0-9]+)?)"
    );

    private static final Map<String, Double> BASE_PRICES = Map.ofEntries(
            // Precious Metals
            Map.entry("XAUUSD", 4062.71),
            Map.entry("XAGUSD", 52.330),
            Map.entry("XPTUSD", 1501.70),
            Map.entry("XPDUSD", 1004.00),
            // Energy Commodities
            Map.entry("USOIL", 88.47),
            Map.entry("UKOIL", 94.62),
            Map.entry("NATGAS", 2.960),
            Map.entry("GASOLINE", 2.7350),
            Map.entry("HEATOIL", 4.2145),
            // Industrial Metals
            Map.entry("COPPER", 6.4150),
            Map.entry("ALUMINUM", 3193.25),
            // Agricultural & Soft Commodities
            Map.entry("WHEAT", 598.50),
            Map.entry("CORN", 444.25),
            Map.entry("SOYBN", 1202.25),
            Map.entry("SUGAR", 18.47),
            Map.entry("COFFEE", 279.50),
            Map.entry("COCOA", 5427.00),
            Map.entry("COTTON", 78.15),
            // Forex Pairs
            Map.entry("EURUSD", 1.12371),
            Map.entry("GBPUSD", 1.34733),
            Map.entry("USDJPY", 158.297),
            Map.entry("USDCHF", 0.81709),
            Map.entry("AUDUSD", 0.70090),
            Map.entry("USDCAD", 1.39672),
            Map.entry("NZDUSD", 0.56247),
            Map.entry("USDTRY", 50.0178),
            // US Equities
            Map.entry("AAPL", 353.89),
            Map.entry("NVDA", 203.64),
            Map.entry("MSFT", 554.05),
            Map.entry("TSLA", 390.40),
            // Global Indices
            Map.entry("SPX500", 7710.25),
            Map.entry("NDX100", 28631.60)
    );

    private static final Map<String, String> TV_TICKER_TO_SYMBOL = Map.ofEntries(
            // Precious Metals
            Map.entry("OANDA:XAUUSD", "XAUUSD"),
            Map.entry("COMEX:SI1!", "XAGUSD"),
            Map.entry("NYMEX:PL1!", "XPTUSD"),
            Map.entry("NYMEX:PA1!", "XPDUSD"),
            // Energy Commodities
            Map.entry("NYMEX:CL1!", "USOIL"),
            Map.entry("ICEEUR:BRN1!", "UKOIL"),
            Map.entry("NYMEX:NG1!", "NATGAS"),
            Map.entry("NYMEX:RB1!", "GASOLINE"),
            Map.entry("NYMEX:HO1!", "HEATOIL"),
            // Industrial Metals
            Map.entry("COMEX:HG1!", "COPPER"),
            Map.entry("COMEX:ALI1!", "ALUMINUM"),
            // Agricultural & Soft Commodities
            Map.entry("CBOT:ZW1!", "WHEAT"),
            Map.entry("CBOT:ZC1!", "CORN"),
            Map.entry("CBOT:ZS1!", "SOYBN"),
            Map.entry("ICEUS:SB1!", "SUGAR"),
            Map.entry("ICEUS:KC1!", "COFFEE"),
            Map.entry("ICEUS:CC1!", "COCOA"),
            Map.entry("ICEUS:CT1!", "COTTON"),
            // Forex Pairs
            Map.entry("FX:EURUSD", "EURUSD"),
            Map.entry("FX:GBPUSD", "GBPUSD"),
            Map.entry("FX:USDJPY", "USDJPY"),
            Map.entry("FX:USDCHF", "USDCHF"),
            Map.entry("FX:AUDUSD", "AUDUSD"),
            Map.entry("FX:USDCAD", "USDCAD"),
            Map.entry("FX:NZDUSD", "NZDUSD"),
            Map.entry("FX:USDTRY", "USDTRY"),
            // US Equities
            Map.entry("NASDAQ:AAPL", "AAPL"),
            Map.entry("NASDAQ:NVDA", "NVDA"),
            Map.entry("NASDAQ:MSFT", "MSFT"),
            Map.entry("NASDAQ:TSLA", "TSLA"),
            // Global Indices
            Map.entry("SP:SPX", "SPX500"),
            Map.entry("NASDAQ:NDX", "NDX100")
    );

    private final Map<String, Double> livePrices = new ConcurrentHashMap<>(BASE_PRICES);
    private final Map<String, Bar> customBackfillStore = new ConcurrentHashMap<>();

    public void refreshLivePricesFromTradingView() {
        try {
            String tickersJson = TV_TICKER_TO_SYMBOL.keySet().stream()
                    .map(t -> "\"" + t + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            String payload = "{\"symbols\":{\"tickers\":[" + tickersJson + "],\"query\":{\"types\":[]}},\"columns\":[\"close\"]}";
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://scanner.tradingview.com/global/scan"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                Matcher matcher = TV_ROW_PATTERN.matcher(response.body());
                while (matcher.find()) {
                    String ticker = matcher.group(1);
                    double close = Double.parseDouble(matcher.group(2));
                    String sym = TV_TICKER_TO_SYMBOL.get(ticker);
                    if (sym != null && close > 0.0) {
                        livePrices.put(sym, close);
                    }
                }
            }
        } catch (Exception ignored) {
            // Offline or sandboxed environment: fallback to BASE_PRICES seamlessly
        }
    }

    public void registerBackfillBar(Bar bar) {
        if (!bar.closed()) {
            throw new IllegalArgumentException("INV-BAR-1: Cannot register a forming bar in OhlcvAdapter");
        }
        customBackfillStore.put(key(bar.symbol(), bar.timeframe(), bar.barIndex()), bar);
    }

    @Override
    public List<Bar> fetchClosedBars(Symbol symbol, Timeframe timeframe, long upToExclusiveBarIndex, int count) {
        if (upToExclusiveBarIndex <= 0 || count <= 0) {
            return List.of();
        }
        long startIdx = Math.max(0, upToExclusiveBarIndex - count);
        List<Bar> bars = new ArrayList<>((int) (upToExclusiveBarIndex - startIdx));
        for (long idx = startIdx; idx < upToExclusiveBarIndex; idx++) {
            bars.add(synthesizeClosedBar(symbol, timeframe, idx));
        }
        return bars;
    }

    @Override
    public Optional<Bar> backfillMissingBar(Symbol symbol, Timeframe timeframe, long missingBarIndex) {
        if (missingBarIndex < 0) {
            return Optional.empty();
        }
        Bar custom = customBackfillStore.get(key(symbol, timeframe, missingBarIndex));
        if (custom != null) {
            return Optional.of(custom);
        }
        return Optional.of(synthesizeClosedBar(symbol, timeframe, missingBarIndex));
    }

    public Bar synthesizeClosedBar(Symbol symbol, Timeframe timeframe, long barIndex) {
        double targetLatestClose = livePrices.getOrDefault(symbol.value(), 100.0);
        int tfShift = (timeframe.ordinal() - Timeframe.M15.ordinal()) * 13;
        int seed = Math.abs(symbol.value().hashCode() % 97) + tfShift;
        double tfAmp = switch (timeframe) {
            case M1 -> 0.35;
            case M5 -> 0.65;
            case M15 -> 1.00;
            case M30 -> 1.25;
            case H1 -> 1.55;
            case H2 -> 1.85;
            case H4 -> 2.20;
            case D1 -> 2.80;
            case W1 -> 3.60;
            case MN -> 4.50;
        };

        // Normalize linear scale so barIndex == 64 closes at targetLatestClose while preserving all indicator ratios
        double refFactorAt64 = rawFactor(seed, tfAmp, REFERENCE_LATEST_BAR_INDEX);
        double scale = targetLatestClose / refFactorAt64;

        double wave = (Math.sin((barIndex + seed) * 0.18) * 0.015
                + Math.cos((barIndex + seed) * 0.05) * 0.022) * tfAmp;
        double drift = (barIndex * 0.0006 * tfAmp) * ((seed % 2 == 0) ? 1.0 : -0.5);
        double openFactor = Math.max(0.1, 1.0 + wave + drift);
        double deltaFactor = Math.sin((barIndex + seed) * 0.37) * 0.006 * tfAmp;
        double closeFactor = Math.max(0.1, openFactor + deltaFactor);
        double highFactor = Math.max(openFactor, closeFactor) + Math.abs(deltaFactor) * 0.65 + 0.0015 * tfAmp;
        double lowFactor = Math.max(0.05, Math.min(openFactor, closeFactor) - Math.abs(deltaFactor) * 0.55 - 0.0012 * tfAmp);

        int decimals = decimalsForSymbol(symbol.value());
        double open = round(openFactor * scale, decimals);
        double close = round(closeFactor * scale, decimals);
        double high = Math.max(Math.max(open, close), round(highFactor * scale, decimals));
        double low = Math.min(Math.min(open, close), round(lowFactor * scale, decimals));

        double volume = Math.round((10_000.0 + ((barIndex * 37 + Math.abs(seed) * 101) % 15_000)
                * ((barIndex % 7 == 0) ? 1.85 : 1.0)) * tfAmp);

        long stepSec = timeframe.duration().toSeconds();
        Instant latestAnchor = Instant.parse("2026-11-04T18:00:00Z");
        Instant openTime = latestAnchor.minusSeconds((REFERENCE_LATEST_BAR_INDEX - barIndex) * stepSec);
        Instant closeTime = openTime.plusSeconds(stepSec);
        return new Bar(symbol, timeframe, barIndex, openTime, closeTime, open, high, low, close, volume, true);
    }

    private static double rawFactor(int seed, double tfAmp, long barIndex) {
        double wave = (Math.sin((barIndex + seed) * 0.18) * 0.015
                + Math.cos((barIndex + seed) * 0.05) * 0.022) * tfAmp;
        double drift = (barIndex * 0.0006 * tfAmp) * ((seed % 2 == 0) ? 1.0 : -0.5);
        double openFactor = Math.max(0.1, 1.0 + wave + drift);
        double deltaFactor = Math.sin((barIndex + seed) * 0.37) * 0.006 * tfAmp;
        return Math.max(0.1, openFactor + deltaFactor);
    }

    private static int decimalsForSymbol(String sym) {
        return switch (sym) {
            case "EURUSD", "GBPUSD", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD" -> 5;
            case "USDTRY", "GASOLINE", "HEATOIL", "COPPER" -> 4;
            case "USDJPY", "XAGUSD", "NATGAS" -> 3;
            default -> 2;
        };
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static String key(Symbol symbol, Timeframe timeframe, long barIndex) {
        return symbol.value() + ":" + timeframe.name() + ":" + barIndex;
    }
}


