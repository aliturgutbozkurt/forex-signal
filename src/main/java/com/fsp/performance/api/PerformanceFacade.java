package com.fsp.performance.api;

import com.fsp.marketdata.api.BarClosed;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.List;

public interface PerformanceFacade {

    PerformanceMetricsDto getOverallPaperTradingMetrics();

    List<PerformanceMetricsDto> getMetricsByTimeframe();

    BacktestRunResultDto runBacktestAndWalkForward(
            Symbol symbol,
            Timeframe timeframe,
            List<BarClosed> historicalBars,
            CostModel costModel
    );

    void recordClosedTrade(Symbol symbol, Timeframe timeframe, String strategyId, double realizedRMultiple);
}

