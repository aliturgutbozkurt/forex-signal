package com.fsp.marketdata.adapter.out;

import com.fsp.marketdata.application.MarketDataPort;
import com.fsp.marketdata.domain.Bar;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * OHLCV secondary adapter supporting deterministic historical bar generation and gap backfill
 * while strictly guaranteeing INV-BAR-1 (forming bars are never emitted).
 */
@NullMarked
@SecondaryAdapter
@Component
public class OhlcvAdapter implements MarketDataPort {

    private static final Instant EPOCH_ANCHOR = Instant.parse("2026-01-01T00:00:00Z");

    private static final Map<String, Double> BASE_PRICES = Map.ofEntries(
            Map.entry("EURUSD", 1.08450),
            Map.entry("GBPUSD", 1.29400),
            Map.entry("USDJPY", 152.350),
            Map.entry("USDTRY", 34.2850),
            Map.entry("THYAO", 312.50),
            Map.entry("GARAN", 124.80),
            Map.entry("ASELS", 72.45),
            Map.entry("AKBNK", 64.20),
            Map.entry("KCHOL", 198.60),
            Map.entry("EREGL", 51.30),
            Map.entry("AAPL", 232.40),
            Map.entry("NVDA", 141.80),
            Map.entry("MSFT", 428.50),
            Map.entry("TSLA", 258.90),
            Map.entry("XAUUSD", 2745.60),
            Map.entry("XAGUSD", 33.85),
            Map.entry("USOIL", 71.40),
            Map.entry("XU100", 9850.00),
            Map.entry("SPX500", 5860.00),
            Map.entry("NDX100", 20480.00)
    );

    private final Map<String, Bar> customBackfillStore = new ConcurrentHashMap<>();

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
        double base = BASE_PRICES.getOrDefault(symbol.value(), 100.0);
        int symSeed = Math.abs(symbol.value().hashCode() % 97);
        double wave = Math.sin((barIndex + symSeed) * 0.18) * (base * 0.015)
                + Math.cos((barIndex + symSeed) * 0.05) * (base * 0.022);
        double drift = (barIndex * 0.0006 * base) * ((symSeed % 2 == 0) ? 1.0 : -0.5);
        double open = Math.max(base * 0.1, base + wave + drift);
        double delta = Math.sin((barIndex + symSeed) * 0.37) * (base * 0.006);
        double close = Math.max(base * 0.1, open + delta);
        double high = Math.max(open, close) + Math.abs(delta) * 0.65 + (base * 0.0015);
        double low = Math.max(base * 0.05, Math.min(open, close) - Math.abs(delta) * 0.55 - (base * 0.0012));
        double volume = 10_000.0 + ((barIndex * 37 + symSeed * 101) % 15_000)
                * ((barIndex % 7 == 0) ? 1.85 : 1.0);

        long stepSec = timeframe.duration().toSeconds();
        Instant openTime = EPOCH_ANCHOR.plusSeconds(barIndex * stepSec);
        Instant closeTime = openTime.plusSeconds(stepSec);
        return new Bar(symbol, timeframe, barIndex, openTime, closeTime, open, high, low, close, volume, true);
    }

    private static String key(Symbol symbol, Timeframe timeframe, long barIndex) {
        return symbol.value() + ":" + timeframe.name() + ":" + barIndex;
    }
}

