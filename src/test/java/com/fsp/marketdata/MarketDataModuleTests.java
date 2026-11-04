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
    void searchesInstrumentCatalogUnder100msAndResolvesThyaoInTryAndBistSession() {
        TradingViewScannerAdapter scanner = new TradingViewScannerAdapter();
        OhlcvAdapter ohlcv = new OhlcvAdapter();
        List<Object> publishedEvents = new ArrayList<>();
        MarketDataApplicationService service = new MarketDataApplicationService(scanner, ohlcv, publishedEvents::add);

        long startNanos = System.nanoTime();
        List<InstrumentDto> results = service.searchInstruments("THY", null);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(elapsedMs).isLessThan(100L);
        assertThat(results).hasSize(1);
        InstrumentDto thyao = results.getFirst();
        assertThat(thyao.symbol()).isEqualTo(Symbol.of("THYAO"));
        assertThat(thyao.exchange()).isEqualTo("BIST");
        assertThat(thyao.assetClass()).isEqualTo(AssetClass.BIST_EQUITY);
        assertThat(thyao.quoteCurrency()).isEqualTo(Currency.TRY);
        assertThat(thyao.timezone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
        assertThat(thyao.delayedFeed()).isTrue();

        // Check BIST session open at 11:30 Istanbul and closed on Republic Day holiday (2026-10-29)
        Instant openSessionInstant = ZonedDateTime.of(2026, 11, 4, 11, 30, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant();
        Instant afterHoursInstant = ZonedDateTime.of(2026, 11, 4, 19, 0, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant();
        Instant holidayInstant = ZonedDateTime.of(2026, 10, 29, 11, 30, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant();

        assertThat(service.isSymbolOpenAt(Symbol.of("THYAO"), openSessionInstant)).isTrue();
        assertThat(service.isSymbolOpenAt(Symbol.of("THYAO"), afterHoursInstant)).isFalse();
        assertThat(service.isSymbolOpenAt(Symbol.of("THYAO"), holidayInstant)).isFalse();
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

        int scanned = service.executeParallelScanCycle(Timeframe.M15, List.of(Symbol.of("EURUSD"), Symbol.of("THYAO"), Symbol.of("AAPL")));
        assertThat(scanned).isEqualTo(3);
    }
}

