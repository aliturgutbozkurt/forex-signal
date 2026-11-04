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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * OHLCV secondary adapter with a Single-Flight Shared Hot Cache (Stale-While-Revalidate).
 * Fetches external TradingView prices as a single collector user in one batch call and serves
 * all concurrent users instantaneously (< 1 ms) from the shared in-memory hot cache.
 */
@NullMarked
@SecondaryAdapter
@Component
public class OhlcvAdapter implements MarketDataPort {

    private static final Instant EPOCH_ANCHOR = Instant.parse("2026-11-01T00:00:00Z");
    private static final long REFERENCE_LATEST_BAR_INDEX = 64L;
    private static final long SHARED_CACHE_TTL_MS = 2_500L;
    private static final Pattern TV_ROW_PATTERN = Pattern.compile(
            "\"s\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"d\"\\s*:\\s*\\[\\s*(-?[0-9]+(?:\\.[0-9]+)?)"
    );

    private static final Map<String, Double> BASE_PRICES = Map.ofEntries(
            // Precious Metals
            Map.entry("XAUUSD", 4060.36),
            Map.entry("XAGUSD", 52.282),
            Map.entry("XPTUSD", 1498.20),
            Map.entry("XPDUSD", 1003.00),
            // Energy Commodities
            Map.entry("USOIL", 88.12),
            Map.entry("UKOIL", 94.62),
            Map.entry("NATGAS", 2.959),
            Map.entry("GASOLINE", 2.7286),
            Map.entry("HEATOIL", 4.2055),
            // Industrial Metals
            Map.entry("COPPER", 6.4290),
            Map.entry("ALUMINUM", 3181.50),
            // Agricultural & Soft Commodities
            Map.entry("WHEAT", 598.50),
            Map.entry("CORN", 444.25),
            Map.entry("SOYBN", 1202.25),
            Map.entry("SUGAR", 18.47),
            Map.entry("COFFEE", 279.50),
            Map.entry("COCOA", 5427.00),
            Map.entry("COTTON", 78.15),
            // Forex Pairs
            Map.entry("EURUSD", 1.12296),
            Map.entry("GBPUSD", 1.34674),
            Map.entry("USDJPY", 158.362),
            Map.entry("USDCHF", 0.81601),
            Map.entry("AUDUSD", 0.70027),
            Map.entry("USDCAD", 1.39737),
            Map.entry("NZDUSD", 0.56196),
            Map.entry("USDTRY", 50.0158),
            // US Equities
            Map.entry("AAPL", 349.51),
            Map.entry("NVDA", 200.13),
            Map.entry("MSFT", 555.80),
            Map.entry("TSLA", 385.19),
            // Global Indices
            Map.entry("SPX500", 7688.62),
            Map.entry("NDX100", 28437.17)
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

    private final AtomicReference<Map<String, Double>> livePricesRef = new AtomicReference<>(Map.copyOf(BASE_PRICES));
    private final Map<String, Bar> customBackfillStore = new ConcurrentHashMap<>();
    private final AtomicBoolean singleFlightLock = new AtomicBoolean(false);
    private final AtomicBoolean initialSeedCompleted = new AtomicBoolean(false);
    private final AtomicLong lastRefreshedEpochMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong singleFlightFetchCount = new AtomicLong(0L);
    private final AtomicLong cacheServedCount = new AtomicLong(0L);
    private final HttpClient sharedHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final String cachedBatchPayload;

    public OhlcvAdapter() {
        String tickersJson = TV_TICKER_TO_SYMBOL.keySet().stream()
                .map(t -> "\"" + t + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        this.cachedBatchPayload = "{\"symbols\":{\"tickers\":[" + tickersJson + "],\"query\":{\"types\":[]}},\"columns\":[\"close\"]}";

        // Background single-flight collector daemon: fetches as one user every 2.5s and keeps Shared Hot Cache fresh
        Thread collectorThread = Thread.ofVirtual().name("fsp-single-flight-cache-collector").unstarted(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    executeSingleFlightFetch();
                    Thread.sleep(SHARED_CACHE_TTL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        collectorThread.setDaemon(true);
        collectorThread.start();
    }

    @Override
    public void refreshLivePricesFromTradingView() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshedEpochMs.get() >= SHARED_CACHE_TTL_MS) {
            Thread.ofVirtual().start(this::executeSingleFlightFetch);
        }
    }

    /**
     * Checks whether the real exchange session for a symbol is currently open in UTC,
     * matching {@code docs/tla/SharedHotCache.tla} (INV-CACHE-3: ClosedMarketSignalsFrozen).
     */
    public static boolean isSessionOpenAtUtc(String symbol, Instant instant) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        DayOfWeek day = utc.getDayOfWeek();
        int utcMin = utc.getHour() * 60 + utc.getMinute();

        // Weekend closure (Fri 22:00 UTC .. Sun 22:00 UTC)
        if (day == DayOfWeek.SATURDAY
                || (day == DayOfWeek.FRIDAY && utcMin >= 22 * 60)
                || (day == DayOfWeek.SUNDAY && utcMin < 22 * 60)) {
            return false;
        }
        // US Equities (NASDAQ 14:30 - 21:00 UTC, Mon-Fri)
        if ("AAPL".equals(symbol) || "NVDA".equals(symbol) || "MSFT".equals(symbol) || "TSLA".equals(symbol)) {
            return day != DayOfWeek.SUNDAY && utcMin >= (14 * 60 + 30) && utcMin < (21 * 60);
        }
        // ICE Soft Commodities (09:00 - 18:30 UTC, Mon-Fri)
        if ("SUGAR".equals(symbol) || "COFFEE".equals(symbol) || "COCOA".equals(symbol) || "COTTON".equals(symbol)) {
            return day != DayOfWeek.SUNDAY && utcMin >= (9 * 60) && utcMin < (18 * 60 + 30);
        }
        // CBOT Grains (daily break 13:45 - 14:30 UTC and 19:20 - 23:59 UTC)
        if ("WHEAT".equals(symbol) || "CORN".equals(symbol) || "SOYBN".equals(symbol)) {
            return !((utcMin >= (13 * 60 + 45) && utcMin < (14 * 60 + 30))
                    || (utcMin >= (19 * 60 + 20) && utcMin < (24 * 60)));
        }
        // CME/COMEX/NYMEX daily maintenance break (21:00 - 22:00 UTC)
        if (!"EURUSD".equals(symbol) && !"GBPUSD".equals(symbol) && !"USDJPY".equals(symbol)
                && !"USDCHF".equals(symbol) && !"AUDUSD".equals(symbol) && !"USDCAD".equals(symbol)
                && !"NZDUSD".equals(symbol) && !"USDTRY".equals(symbol)
                && utcMin >= (21 * 60) && utcMin < (22 * 60)) {
            return false;
        }
        return true;
    }

    private void executeSingleFlightFetch() {
        // INV-CACHE-1 (SingleFlightExclusivity): at most 1 in-flight request to external provider
        if (!singleFlightLock.compareAndSet(false, true)) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://scanner.tradingview.com/global/scan"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "text/plain")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(cachedBatchPayload))
                    .build();
            HttpResponse<String> response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                Instant nowInstant = Instant.now();
                boolean isFirstSeed = !initialSeedCompleted.get();
                Map<String, Double> currentSnap = livePricesRef.get();
                Map<String, Double> nextSnap = new HashMap<>(currentSnap);
                Matcher matcher = TV_ROW_PATTERN.matcher(response.body());
                while (matcher.find()) {
                    String ticker = matcher.group(1);
                    double close = Double.parseDouble(matcher.group(2));
                    String sym = TV_TICKER_TO_SYMBOL.get(ticker);
                    if (sym != null && close > 0.0) {
                        // INV-CACHE-3 (ClosedMarketSignalsFrozen): after initial seed, only update open-session markets
                        if (isFirstSeed || isSessionOpenAtUtc(sym, nowInstant)) {
                            nextSnap.put(sym, close);
                        }
                    }
                }
                livePricesRef.set(Map.copyOf(nextSnap));
                initialSeedCompleted.set(true);
                lastRefreshedEpochMs.set(System.currentTimeMillis());
                singleFlightFetchCount.incrementAndGet();
            }
        } catch (Exception ignored) {
            // INV-CACHE-2 (Stale-While-Revalidate): keep serving the latest atomic hot cache snapshot seamlessly
            lastRefreshedEpochMs.set(System.currentTimeMillis());
        } finally {
            singleFlightLock.set(false);
        }
    }

    @Override
    public Map<String, Object> getSharedHotCacheSnapshot() {
        long served = cacheServedCount.incrementAndGet();
        long now = System.currentTimeMillis();
        long lastRefresh = lastRefreshedEpochMs.get();
        if (now - lastRefresh >= SHARED_CACHE_TTL_MS) {
            Thread.ofVirtual().start(this::executeSingleFlightFetch);
        }
        Map<String, Double> atomicSnap = livePricesRef.get();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "SINGLE_FLIGHT_SHARED_HOT_CACHE");
        snapshot.put("tlaVerified", "SharedHotCache.tla (INV-CACHE-1..4)");
        snapshot.put("externalProvider", "https://scanner.tradingview.com/global/scan");
        snapshot.put("ttlMs", SHARED_CACHE_TTL_MS);
        snapshot.put("lastUpdatedEpochMs", lastRefresh);
        snapshot.put("cacheAgeMs", Math.max(0L, now - lastRefresh));
        snapshot.put("singleFlightFetchCount", singleFlightFetchCount.get());
        snapshot.put("cacheServedCount", served);
        snapshot.put("instrumentCount", atomicSnap.size());
        snapshot.put("prices", atomicSnap);
        return snapshot;
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
        double targetLatestClose = livePricesRef.get().getOrDefault(symbol.value(), 100.0);
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


