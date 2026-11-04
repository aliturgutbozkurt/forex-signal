package com.fsp.analysis.api;

import com.fsp.marketdata.api.BarClosed;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.Optional;

public interface AnalysisFacade {

    Optional<IndicatorSnapshotDto> getLatestSnapshot(Symbol symbol, Timeframe timeframe);

    IndicatorSnapshotDto onBarClosed(BarClosed bar);
}

