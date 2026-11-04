package com.fsp.marketdata.api;

import com.fsp.shared.AssetClass;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface MarketDataFacade {

    List<InstrumentDto> searchInstruments(@Nullable String query, @Nullable AssetClass assetClassFilter);

    Optional<InstrumentDto> findInstrument(Symbol symbol);

    List<InstrumentDto> allInstruments();

    boolean isSymbolOpenAt(Symbol symbol, Instant instant);

    List<BarClosed> getEvaluatedBars(Symbol symbol, Timeframe timeframe, int maxCount);

    ScannerIndicatorValues getScannerSnapshot(Symbol symbol, Timeframe timeframe);

    boolean isDegradedMode();

    void setDegradedMode(boolean degraded, String reason);

    List<BarClosed> ingestAndEvaluateBars(Symbol symbol, Timeframe timeframe, long nowBarIndex, List<BarClosed> candidateBars);

    int executeParallelScanCycle(Timeframe timeframe, List<Symbol> symbols);

    default void refreshLiveQuotes() {}
}

