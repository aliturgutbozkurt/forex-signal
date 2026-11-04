package com.fsp.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsp.marketdata.adapter.out.OhlcvAdapter;
import com.fsp.marketdata.adapter.out.TradingViewScannerAdapter;
import com.fsp.marketdata.api.BarClosed;
import com.fsp.marketdata.api.DataSourceDegraded;
import com.fsp.marketdata.api.InstrumentDto;
import com.fsp.marketdata.application.MarketDataApplicationService;
import com.fsp.marketdata.domain.InstrumentId;
import com.fsp.marketdata.domain.TradingSession;
import com.fsp.shared.AssetClass;
import com.fsp.shared.Currency;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("FR-01")
@Tag("FR-17")
@Tag("M1")
class MarketDataModuleTests {

    @Test
    @Tag("FR-01")
    void searchesInstrumentCatalogUnder100msIncludesAll18ForexCommoditiesAndExcludesBist() {
        TradingViewScannerAdapter scanner = new TradingViewScannerAdapter();
        OhlcvAdapter ohlcv = new OhlcvAdapter();
        List<Object> publishedEvents = new ArrayList<>();
        MarketDataApplicationService service = new MarketDataApplicationService(scanner, ohlcv, publishedEvents::add);

        long startNanos = System.nanoTime();
        List<InstrumentDto> results = service.searchInstruments("XAU", null);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(elapsedMs).isLessThan(100L);
        assertThat(results).hasSize(1);
        InstrumentDto xauusd = results.getFirst();
        assertThat(xauusd.symbol()).isEqualTo(Symbol.of("XAUUSD"));
        assertThat(xauusd.exchange()).isEqualTo("OANDA");
        assertThat(xauusd.assetClass()).isEqualTo(AssetClass.COMMODITY);
        assertThat(xauusd.quoteCurrency()).isEqualTo(Currency.USD);
        assertThat(xauusd.delayedFeed()).isFalse();

        // BIST equities must not exist in the catalog
        assertThat(service.searchInstruments("THYAO", null)).isEmpty();
        assertThat(service.searchInstruments(null, AssetClass.BIST_EQUITY)).isEmpty();

        // All 18 Forex commodities must be registered
        List<InstrumentDto> commodities = service.searchInstruments(null, AssetClass.COMMODITY);
        assertThat(commodities).hasSize(18);
        assertThat(commodities).extracting(i -> i.symbol().value()).contains(
                "XAUUSD", "XAGUSD", "XPTUSD", "XPDUSD",
                "USOIL", "UKOIL", "NATGAS", "GASOLINE", "HEATOIL",
                "COPPER", "ALUMINUM",
                "WHEAT", "CORN", "SOYBN", "SUGAR", "COFFEE", "COCOA", "COTTON"
        );

        // Forex 24x5 session open on Wednesday and closed on Saturday
        Instant wednesdayInstant = ZonedDateTime.of(2026, 11, 4, 14, 30, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant saturdayInstant = ZonedDateTime.of(2026, 11, 7, 14, 30, 0, 0, ZoneId.of("UTC")).toInstant();

        assertThat(service.isSymbolOpenAt(Symbol.of("XAUUSD"), wednesdayInstant)).isTrue();
        assertThat(service.isSymbolOpenAt(Symbol.of("XAUUSD"), saturdayInstant)).isFalse();
    }

    @Test
    @Tag("FR-17")
    void scannerAdapterBuildsTimeframeColumnsAndEntersDegradedModeWhenCircuitOpens() {
        TradingViewScannerAdapter scanner = new TradingViewScannerAdapter();
        OhlcvAdapter ohlcv = new OhlcvAdapter();
        List<Object> events = new ArrayList<>();
        MarketDataApplicationService service = new MarketDataApplicationService(scanner, ohlcv, events::add);

        assertThat(scanner.buildColumnsForTimeframe(Timeframe.M15))
                .contains("RSI|15", "Recommend.All|15", "ADX|15");
        assertThat(scanner.buildColumnsForTimeframe(Timeframe.D1))
                .contains("RSI", "Recommend.All", "ADX");

        // Trip the circuit breaker
        scanner.setSimulateFailure(true);
        for (int i = 0; i < 6; i++) {
            scanner.fetchBatchSnapshots("forex", List.of(InstrumentId.of("FX", "EURUSD")), Timeframe.M15);
        }
        assertThat(scanner.isCircuitOpen()).isTrue();
        assertThat(service.isDegradedMode()).isTrue();

        service.getScannerSnapshot(Symbol.of("EURUSD"), Timeframe.M15);
        assertThat(events).anyMatch(DataSourceDegraded.class::isInstance);
    }

    @Test
    void barClockExecutesParallelScanAndBackfillsGaps() {
        TradingViewScannerAdapter scanner = new TradingViewScannerAdapter();
        OhlcvAdapter ohlcv = new OhlcvAdapter();
        List<Object> events = new ArrayList<>();
        MarketDataApplicationService service = new MarketDataApplicationService(scanner, ohlcv, events::add);

        // Send only bar #2 when nowBarIndex = 3 -> bars #0 and #1 must be backfilled automatically
        Symbol eurusd = Symbol.of("EURUSD");
        var bar2 = ohlcv.synthesizeClosedBar(eurusd, Timeframe.M5, 2);
        BarClosed candidate2 = new BarClosed(
                eurusd, Timeframe.M5, 2, bar2.openTime(), bar2.closeTime(),
                bar2.open(), bar2.high(), bar2.low(), bar2.close(), bar2.volume(), false
        );
        service.ingestAndEvaluateBars(eurusd, Timeframe.M5, 3, List.of(candidate2));

        List<BarClosed> evaluated = service.getEvaluatedBars(eurusd, Timeframe.M5, 10);
        assertThat(evaluated).extracting(BarClosed::barIndex).containsExactly(0L, 1L, 2L);

        int scanned = service.executeParallelScanCycle(Timeframe.M15, List.of(Symbol.of("EURUSD"), Symbol.of("XAUUSD"), Symbol.of("USOIL")));
        assertThat(scanned).isEqualTo(3);
    }

    @Test
    @Tag("INV-CACHE-1")
    @Tag("INV-CACHE-2")
    @Tag("INV-CACHE-3")
    @Tag("INV-CACHE-4")
    void sharedHotCacheAndSessionFreezeSatisfyTlaInvariants() {
        OhlcvAdapter ohlcv = new OhlcvAdapter();
        var snapshot = ohlcv.getSharedHotCacheSnapshot();

        assertThat(snapshot).containsEntry("source", "SINGLE_FLIGHT_SHARED_HOT_CACHE");
        assertThat(snapshot).containsEntry("tlaVerified", "SharedHotCache.tla (INV-CACHE-1..4)");
        assertThat(snapshot.get("instrumentCount")).isEqualTo(32);

        @SuppressWarnings("unchecked")
        var prices = (java.util.Map<String, Double>) snapshot.get("prices");
        assertThat(prices).hasSize(32);
        assertThat(prices.values()).allMatch(p -> p != null && p > 0.0);

        Instant wednesdayOpenUtc = ZonedDateTime.of(2026, 11, 4, 15, 30, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant wednesdayAfterCloseUtc = ZonedDateTime.of(2026, 11, 4, 22, 30, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant wednesdayCmeBreakUtc = ZonedDateTime.of(2026, 11, 4, 21, 30, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant saturdayClosedUtc = ZonedDateTime.of(2026, 11, 7, 15, 30, 0, 0, ZoneId.of("UTC")).toInstant();

        // US Equities (NASDAQ 14:30 - 21:00 UTC)
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("AAPL", wednesdayOpenUtc)).isTrue();
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("AAPL", wednesdayAfterCloseUtc)).isFalse();
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("AAPL", saturdayClosedUtc)).isFalse();

        // ICE Softs (09:00 - 18:30 UTC)
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("SUGAR", wednesdayOpenUtc)).isTrue();
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("SUGAR", wednesdayAfterCloseUtc)).isFalse();

        // Precious Metals (open 24x5 except 21:00-22:00 UTC maintenance break & weekend)
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("XAUUSD", wednesdayOpenUtc)).isTrue();
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("XAUUSD", wednesdayCmeBreakUtc)).isFalse();
        assertThat(OhlcvAdapter.isSessionOpenAtUtc("XAUUSD", saturdayClosedUtc)).isFalse();
    }
}

