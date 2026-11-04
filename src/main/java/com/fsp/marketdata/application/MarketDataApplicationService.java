package com.fsp.marketdata.application;

import com.fsp.marketdata.api.BarClosed;
import com.fsp.marketdata.api.DataQualityDegraded;
import com.fsp.marketdata.api.DataSourceDegraded;
import com.fsp.marketdata.api.InstrumentDto;
import com.fsp.marketdata.api.MarketDataFacade;
import com.fsp.marketdata.api.ScannerIndicatorValues;
import com.fsp.marketdata.domain.Bar;
import com.fsp.marketdata.domain.BarSeries;
import com.fsp.marketdata.domain.Instrument;
import com.fsp.marketdata.domain.InstrumentId;
import com.fsp.marketdata.domain.TradingSession;
import com.fsp.shared.AssetClass;
import com.fsp.shared.Currency;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class MarketDataApplicationService implements MarketDataFacade {

    private static final int DEFAULT_RING_CAPACITY = 512;
    private static final Set<LocalDate> BIST_HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 23),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 19),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 8, 30),
            LocalDate.of(2026, 10, 29)
    );
    private static final Set<LocalDate> US_HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 12, 25)
    );

    private final Map<Symbol, Instrument> catalog = new LinkedHashMap<>();
    private final Map<String, BarSeries> seriesMap = new ConcurrentHashMap<>();
    private final Map<String, List<BarClosed>> evaluatedHistory = new ConcurrentHashMap<>();
    private final Map<String, ScannerIndicatorValues> latestScannerCache = new ConcurrentHashMap<>();

    private final IndicatorSnapshotPort scannerAdapter;
    private final MarketDataPort ohlcvAdapter;
    private final ApplicationEventPublisher eventPublisher;
    private volatile boolean manualDegraded = false;

    public MarketDataApplicationService(
            IndicatorSnapshotPort scannerAdapter,
            MarketDataPort ohlcvAdapter,
            ApplicationEventPublisher eventPublisher
    ) {
        this.scannerAdapter = scannerAdapter;
        this.ohlcvAdapter = ohlcvAdapter;
        this.eventPublisher = eventPublisher;
        seedCatalog();
    }

    private void seedCatalog() {
        TradingSession forexSession = TradingSession.forex24x5();
        TradingSession bistSession = TradingSession.bistDefault(BIST_HOLIDAYS);
        TradingSession usSession = TradingSession.usEquityDefault(US_HOLIDAYS);

        // Forex
        register(new Instrument(InstrumentId.of("FX", "EURUSD"), "Euro / ABD Doları", AssetClass.FOREX, Currency.USD, new BigDecimal("0.00001"), forexSession, false));
        register(new Instrument(InstrumentId.of("FX", "GBPUSD"), "İngiliz Sterlini / ABD Doları", AssetClass.FOREX, Currency.USD, new BigDecimal("0.00001"), forexSession, false));
        register(new Instrument(InstrumentId.of("FX", "USDJPY"), "ABD Doları / Japon Yeni", AssetClass.FOREX, Currency.JPY, new BigDecimal("0.001"), forexSession, false));
        register(new Instrument(InstrumentId.of("FX", "USDTRY"), "ABD Doları / Türk Lirası", AssetClass.FOREX, Currency.TRY, new BigDecimal("0.0001"), forexSession, false));

        // BIST Equities (15-min delayed badge per SPEC-001 §13 Q2)
        register(new Instrument(InstrumentId.of("BIST", "THYAO"), "Türk Hava Yolları A.O.", AssetClass.BIST_EQUITY, Currency.TRY, new BigDecimal("0.25"), bistSession, true));
        register(new Instrument(InstrumentId.of("BIST", "GARAN"), "Garanti BBVA", AssetClass.BIST_EQUITY, Currency.TRY, new BigDecimal("0.10"), bistSession, true));
        register(new Instrument(InstrumentId.of("BIST", "ASELS"), "Aselsan Elektronik Sanayi", AssetClass.BIST_EQUITY, Currency.TRY, new BigDecimal("0.05"), bistSession, true));
        register(new Instrument(InstrumentId.of("BIST", "AKBNK"), "Akbank T.A.Ş.", AssetClass.BIST_EQUITY, Currency.TRY, new BigDecimal("0.05"), bistSession, true));
        register(new Instrument(InstrumentId.of("BIST", "KCHOL"), "Koç Holding A.Ş.", AssetClass.BIST_EQUITY, Currency.TRY, new BigDecimal("0.10"), bistSession, true));
        register(new Instrument(InstrumentId.of("BIST", "EREGL"), "Erdemir Demir Çelik", AssetClass.BIST_EQUITY, Currency.TRY, new BigDecimal("0.02"), bistSession, true));

        // US Equities
        register(new Instrument(InstrumentId.of("NASDAQ", "AAPL"), "Apple Inc.", AssetClass.US_EQUITY, Currency.USD, new BigDecimal("0.01"), usSession, false));
        register(new Instrument(InstrumentId.of("NASDAQ", "NVDA"), "NVIDIA Corporation", AssetClass.US_EQUITY, Currency.USD, new BigDecimal("0.01"), usSession, false));
        register(new Instrument(InstrumentId.of("NASDAQ", "MSFT"), "Microsoft Corporation", AssetClass.US_EQUITY, Currency.USD, new BigDecimal("0.01"), usSession, false));
        register(new Instrument(InstrumentId.of("NASDAQ", "TSLA"), "Tesla Inc.", AssetClass.US_EQUITY, Currency.USD, new BigDecimal("0.01"), usSession, false));

        // Commodities
        register(new Instrument(InstrumentId.of("OANDA", "XAUUSD"), "Spot Altın (Ons / USD)", AssetClass.COMMODITY, Currency.USD, new BigDecimal("0.01"), forexSession, false));
        register(new Instrument(InstrumentId.of("OANDA", "XAGUSD"), "Spot Gümüş (Ons / USD)", AssetClass.COMMODITY, Currency.USD, new BigDecimal("0.001"), forexSession, false));
        register(new Instrument(InstrumentId.of("TVC", "USOIL"), "WTI Ham Petrol", AssetClass.COMMODITY, Currency.USD, new BigDecimal("0.01"), forexSession, false));

        // Indices
        register(new Instrument(InstrumentId.of("BIST", "XU100"), "BIST 100 Endeksi", AssetClass.INDEX, Currency.PTS, new BigDecimal("0.01"), bistSession, true));
        register(new Instrument(InstrumentId.of("SP", "SPX500"), "S&P 500 Endeksi", AssetClass.INDEX, Currency.USD, new BigDecimal("0.10"), usSession, false));
        register(new Instrument(InstrumentId.of("NASDAQ", "NDX100"), "Nasdaq 100 Endeksi", AssetClass.INDEX, Currency.USD, new BigDecimal("0.10"), usSession, false));
    }

    private void register(Instrument instrument) {
        catalog.put(instrument.symbol(), instrument);
    }

    @Override
    public List<InstrumentDto> searchInstruments(@Nullable String query, @Nullable AssetClass assetClassFilter) {
        Instant now = Instant.now();
        return catalog.values().stream()
                .filter(inst -> assetClassFilter == null || inst.assetClass() == assetClassFilter)
                .filter(inst -> inst.matchesQuery(query == null ? "" : query))
                .map(inst -> toDto(inst, now))
                .toList();
    }

    @Override
    public Optional<InstrumentDto> findInstrument(Symbol symbol) {
        Instrument inst = catalog.get(symbol);
        return inst == null ? Optional.empty() : Optional.of(toDto(inst, Instant.now()));
    }

    @Override
    public List<InstrumentDto> allInstruments() {
        Instant now = Instant.now();
        return catalog.values().stream()
                .map(inst -> toDto(inst, now))
                .toList();
    }

    @Override
    public boolean isSymbolOpenAt(Symbol symbol, Instant instant) {
        Instrument inst = catalog.get(symbol);
        return inst != null && inst.session().isOpenAt(instant);
    }

    @Override
    public List<BarClosed> getEvaluatedBars(Symbol symbol, Timeframe timeframe, int maxCount) {
        String k = seriesKey(symbol, timeframe);
        List<BarClosed> list = evaluatedHistory.computeIfAbsent(k, _ -> Collections.synchronizedList(new ArrayList<>()));
        if (list.isEmpty()) {
            // Seed initial 65 closed bars so indicators and charts are warm immediately
            List<Bar> seeded = ohlcvAdapter.fetchClosedBars(symbol, timeframe, 65, 65);
            List<BarClosed> candidates = seeded.stream()
                    .map(b -> new BarClosed(b.symbol(), b.timeframe(), b.barIndex(), b.openTime(), b.closeTime(),
                            b.open(), b.high(), b.low(), b.close(), b.volume(), false))
                    .toList();
            ingestAndEvaluateBars(symbol, timeframe, 65, candidates);
        }
        synchronized (list) {
            if (list.size() <= maxCount) {
                return List.copyOf(list);
            }
            return List.copyOf(list.subList(list.size() - maxCount, list.size()));
        }
    }

    @Override
    public ScannerIndicatorValues getScannerSnapshot(Symbol symbol, Timeframe timeframe) {
        String k = seriesKey(symbol, timeframe);
        ScannerIndicatorValues cached = latestScannerCache.get(k);
        if (cached != null) {
            return cached;
        }
        Instrument inst = catalog.get(symbol);
        if (inst == null) {
            return ScannerIndicatorValues.unavailable();
        }
        if (scannerAdapter.isCircuitOpen()) {
            setDegradedMode(true, "TradingView Scanner circuit breaker open");
            return ScannerIndicatorValues.unavailable();
        }
        if (manualDegraded) {
            return ScannerIndicatorValues.unavailable();
        }
        Map<InstrumentId, ScannerIndicatorValues> fetched = scannerAdapter.fetchBatchSnapshots(
                inst.assetClass().scannerMarket(),
                List.of(inst.id()),
                timeframe
        );
        if (scannerAdapter.isCircuitOpen()) {
            setDegradedMode(true, "TradingView Scanner circuit breaker open");
        }
        ScannerIndicatorValues val = fetched.getOrDefault(inst.id(), ScannerIndicatorValues.unavailable());
        latestScannerCache.put(k, val);
        return val;
    }

    @Override
    public boolean isDegradedMode() {
        return manualDegraded || scannerAdapter.isCircuitOpen();
    }

    @Override
    public void setDegradedMode(boolean degraded, String reason) {
        boolean wasDegraded = this.manualDegraded;
        this.manualDegraded = degraded;
        if (degraded && !wasDegraded) {
            eventPublisher.publishEvent(new DataSourceDegraded("TradingViewScanner", reason, Instant.now()));
        }
    }

    public void checkCrossSourceDivergence(Symbol symbol, Timeframe timeframe, double scannerRsi, double engineRsi) {
        double diff = Math.abs(scannerRsi - engineRsi);
        if (diff > 10.0) {
            eventPublisher.publishEvent(new DataQualityDegraded(symbol, timeframe, diff, Instant.now()));
        }
    }

    @Override
    public List<BarClosed> ingestAndEvaluateBars(
            Symbol symbol,
            Timeframe timeframe,
            long nowBarIndex,
            List<BarClosed> candidateBars
    ) {
        String k = seriesKey(symbol, timeframe);
        BarSeries series = seriesMap.computeIfAbsent(k, _ -> new BarSeries(symbol, timeframe, DEFAULT_RING_CAPACITY));
        Instrument instrument = catalog.get(symbol);

        synchronized (series) {
            series.advanceClockTo(nowBarIndex);
            for (BarClosed candidate : candidateBars) {
                boolean isClosed = candidate.barIndex() < nowBarIndex;
                Bar domainBar = new Bar(
                        candidate.symbol(),
                        candidate.timeframe(),
                        candidate.barIndex(),
                        candidate.openTime(),
                        candidate.closeTime(),
                        candidate.open(),
                        candidate.high(),
                        candidate.low(),
                        candidate.close(),
                        candidate.volume(),
                        isClosed
                );
                series.receive(domainBar);
            }

            // LIV-BAR-1: Backfill any gaps below nowBarIndex so watermark can advance without gaps
            OptionalLong missing = series.missingWatermarkIndex();
            while (missing.isPresent()) {
                long missingIdx = missing.getAsLong();
                Optional<Bar> backfilled = ohlcvAdapter.backfillMissingBar(symbol, timeframe, missingIdx);
                if (backfilled.isEmpty()) {
                    break;
                }
                series.receive(backfilled.get());
                series.drainEvaluatableBars().forEach(b -> publishEvaluatedBar(series, instrument, b));
                missing = series.missingWatermarkIndex();
            }

            List<Bar> newlyEvaluated = series.drainEvaluatableBars();
            List<BarClosed> events = new ArrayList<>(newlyEvaluated.size());
            for (Bar bar : newlyEvaluated) {
                events.add(publishEvaluatedBar(series, instrument, bar));
            }
            return events;
        }
    }

    /**
     * BarClock fan-out over virtual threads with a strict deadline per SPEC-001 §7.3 & §7.5.
     */
    @Override
    public int executeParallelScanCycle(Timeframe timeframe, List<Symbol> symbols) {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        List<Symbol> targets = symbols.isEmpty() ? List.copyOf(catalog.keySet()) : symbols;
        try (ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Integer>> tasks = targets.stream()
                    .<Callable<Integer>>map(sym -> () -> {
                        String k = seriesKey(sym, timeframe);
                        BarSeries series = seriesMap.computeIfAbsent(
                                k, _ -> new BarSeries(sym, timeframe, DEFAULT_RING_CAPACITY));
                        long nextNow = Math.max(1L, series.nowIndex() + 1L);
                        Bar synthesized = ohlcvAdapter.synthesizeClosedBar(sym, timeframe, nextNow - 1);
                        BarClosed candidate = new BarClosed(
                                sym,
                                timeframe,
                                synthesized.barIndex(),
                                synthesized.openTime(),
                                synthesized.closeTime(),
                                synthesized.open(),
                                synthesized.high(),
                                synthesized.low(),
                                synthesized.close(),
                                synthesized.volume(),
                                false
                        );
                        return ingestAndEvaluateBars(sym, timeframe, nextNow, List.of(candidate)).size();
                    })
                    .toList();

            List<Future<Integer>> futures = vThreads.invokeAll(tasks, 1800, TimeUnit.MILLISECONDS);
            int totalPublished = 0;
            for (Future<Integer> f : futures) {
                if (!f.isCancelled()) {
                    totalPublished += f.get();
                }
            }
            return totalPublished;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    private BarClosed publishEvaluatedBar(BarSeries series, @Nullable Instrument instrument, Bar bar) {
        String k = seriesKey(bar.symbol(), bar.timeframe());
        List<BarClosed> history = evaluatedHistory.computeIfAbsent(
                k, _ -> Collections.synchronizedList(new ArrayList<>()));
        boolean newSessionDay = false;
        synchronized (history) {
            if (!history.isEmpty() && instrument != null) {
                BarClosed prev = history.getLast();
                newSessionDay = instrument.session().isNewSessionDay(prev.openTime(), bar.openTime());
            }
            BarClosed event = new BarClosed(
                    bar.symbol(),
                    bar.timeframe(),
                    bar.barIndex(),
                    bar.openTime(),
                    bar.closeTime(),
                    bar.open(),
                    bar.high(),
                    bar.low(),
                    bar.close(),
                    bar.volume(),
                    newSessionDay
            );
            history.add(event);
            eventPublisher.publishEvent(event);
            return event;
        }
    }

    private static InstrumentDto toDto(Instrument inst, Instant now) {
        return new InstrumentDto(
                inst.symbol(),
                inst.exchange(),
                inst.displayName(),
                inst.assetClass(),
                inst.quoteCurrency(),
                inst.tickSize(),
                inst.session().timezone(),
                inst.delayedFeed(),
                inst.session().isOpenAt(now)
        );
    }

    private static String seriesKey(Symbol symbol, Timeframe timeframe) {
        return symbol.value() + ":" + timeframe.name();
    }
}
