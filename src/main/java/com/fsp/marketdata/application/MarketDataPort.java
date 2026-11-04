package com.fsp.marketdata.application;

import com.fsp.marketdata.domain.Bar;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface MarketDataPort {

    List<Bar> fetchClosedBars(Symbol symbol, Timeframe timeframe, long upToExclusiveBarIndex, int count);

    Optional<Bar> backfillMissingBar(Symbol symbol, Timeframe timeframe, long missingBarIndex);

    Bar synthesizeClosedBar(Symbol symbol, Timeframe timeframe, long barIndex);
}
