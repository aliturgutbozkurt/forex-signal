package com.fsp.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsp.marketdata.adapter.out.OhlcvAdapter;
import com.fsp.marketdata.api.BarClosed;
import com.fsp.performance.api.BacktestRunResultDto;
import com.fsp.performance.api.CostModel;
import com.fsp.performance.api.PerformanceMetricsDto;
import com.fsp.performance.application.PerformanceApplicationService;
import com.fsp.performance.domain.MetricsCalculator;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.signal.api.SignalDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("FR-11")
@Tag("FR-12")
@Tag("FR-13")
@Tag("M6")
class PerformanceAndBacktestTests {

    @Test
    @Tag("FR-12")
    void shiftTestProvesZeroLookAheadBiasWhenFutureBarsAreShocked() {
        OhlcvAdapter ohlcv = new OhlcvAdapter();
        Symbol eurusd = Symbol.of("EURUSD");
        List<BarClosed> prefixBars = ohlcv.fetchClosedBars(eurusd, Timeframe.M15, 60, 60).stream()
                .map(b -> new BarClosed(b.symbol(), b.timeframe(), b.barIndex(), b.openTime(), b.closeTime(),
                        b.open(), b.high(), b.low(), b.close(), b.volume(), false))
                .toList();

        // Create an extended list [0..89] where future bars [60..89] have an extreme 50x price shock
        List<BarClosed> shockedExtendedBars = new ArrayList<>(prefixBars);
        for (int idx = 60; idx < 90; idx++) {
            var b = ohlcv.synthesizeClosedBar(eurusd, Timeframe.M15, idx);
            shockedExtendedBars.add(new BarClosed(
                    eurusd, Timeframe.M15, idx, b.openTime(), b.closeTime(),
                    b.open() * 50.0, b.high() * 55.0, b.low() * 45.0, b.close() * 52.0, b.volume() * 100.0, false
            ));
        }

        PerformanceApplicationService service = new PerformanceApplicationService();
        BacktestRunResultDto prefixRun = service.runBacktestAndWalkForward(
                eurusd, Timeframe.M15, prefixBars, CostModel.realisticDefault());
        BacktestRunResultDto extendedRun = service.runBacktestAndWalkForward(
                eurusd, Timeframe.M15, shockedExtendedBars, CostModel.realisticDefault());

        List<SignalDto> extendedFilteredToPrefix = extendedRun.generatedSignals().stream()
                .filter(s -> s.openedAtBar() < 60)
                .toList();

        // Shift test invariant: future bars [60..89] cannot alter a single signal opened in [0..59]
        assertThat(prefixRun.generatedSignals()).isNotEmpty();
        assertThat(extendedFilteredToPrefix).isEqualTo(prefixRun.generatedSignals());
        assertThat(extendedRun.monteCarlo().simulations()).isEqualTo(200);
    }

    @Test
    @Tag("FR-11")
    @Tag("FR-13")
    void enforces200TradeSampleSizeAndQualityGatesAndUpdatesPaperTradingEquityCurve() {
        MetricsCalculator calc = new MetricsCalculator();

        // < 200 trades -> automatically marked EXPERIMENTAL even with high win rate
        List<Double> smallSample = List.of(1.5, 1.5, -1.0, 2.5, 1.5);
        PerformanceMetricsDto smallMetrics = calc.compute("Small Sample", smallSample, 5.0);
        assertThat(smallMetrics.experimental()).isTrue();
        assertThat(smallMetrics.qualityBadge()).isEqualTo("EXPERIMENTAL");

        // >= 200 trades meeting all SPEC-001 §9 targets -> VERIFIED
        List<Double> verifiedSample = new ArrayList<>(220);
        for (int i = 0; i < 220; i++) {
            // 60% win rate at +1.5R, 40% loss at -1.0R -> PF = 2.25, Expectancy = 0.50R
            verifiedSample.add((i % 5 < 3) ? 1.5 : -1.0);
        }
        PerformanceMetricsDto verifiedMetrics = calc.compute("Verified Strategy", verifiedSample, 12.0);
        assertThat(verifiedMetrics.totalTrades()).isEqualTo(220);
        assertThat(verifiedMetrics.winRatePct()).isGreaterThanOrEqualTo(55.0);
        assertThat(verifiedMetrics.profitFactor()).isGreaterThanOrEqualTo(1.5);
        assertThat(verifiedMetrics.expectancyR()).isGreaterThan(0.25);
        assertThat(verifiedMetrics.maxDrawdownPct()).isLessThanOrEqualTo(15.0);
        assertThat(verifiedMetrics.experimental()).isFalse();
        assertThat(verifiedMetrics.qualityBadge()).isEqualTo("VERIFIED");

        // Paper-trading live equity curve update
        PerformanceApplicationService service = new PerformanceApplicationService();
        int initialCurvePoints = service.getOverallPaperTradingMetrics().equityCurve().size();
        service.recordClosedTrade(Symbol.of("THYAO"), Timeframe.H1, "FSP-CONFLUENCE-CORE", 2.5);
        assertThat(service.getOverallPaperTradingMetrics().equityCurve())
                .hasSize(initialCurvePoints + 1);
    }
}

