package com.fsp.performance.api;

import com.fsp.marketdata.api.BarClosed;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.signal.api.SignalDto;
import java.util.List;

public record BacktestRunResultDto(
        Symbol symbol,
        Timeframe timeframe,
        PerformanceMetricsDto metrics,
        MonteCarloReportDto monteCarlo,
        List<SignalDto> generatedSignals
) {}

