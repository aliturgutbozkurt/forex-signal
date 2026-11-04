package com.fsp.signal.api;

import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.List;
import java.util.Optional;

public interface SignalFacade {

    List<SignalDto> listRecentSignals(int limit);

    List<SignalDto> listSignalsForSymbol(Symbol symbol);

    Optional<SignalDto> findSignalById(String signalId);

    void attachExplanation(String signalId, String explanation);

    StrategySummaryDto getActiveStrategy();

    StrategySummaryDto updateStrategyParameters(
            double slAtrMultiplier,
            double tp1R,
            double tp2R,
            double tp3R,
            int cooldownBars,
            int maxHoldBars,
            String actor
    );

    Optional<SignalDto> evaluateSnapshot(IndicatorSnapshotDto snapshot);
}

