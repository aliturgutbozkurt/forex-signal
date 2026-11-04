package com.fsp.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fsp.analysis.api.DivergenceType;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.analysis.api.MarketRegimeChanged;
import com.fsp.analysis.api.PivotLevels;
import com.fsp.analysis.application.TechnicalAnalysisService;
import com.fsp.analysis.domain.IncrementalIndicatorState;
import com.fsp.analysis.domain.RegimeClassifier;
import com.fsp.marketdata.api.BarClosed;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SPEC-001")
@Tag("M2")
class TechnicalAnalysisGoldenTests {

    @Test
    void trendIndicatorsMatchReferenceFormulaWith1e6ToleranceAndUnder5MicrosecondsPerBar() {
        IncrementalIndicatorState state = new IncrementalIndicatorState();
        double expectedEma9 = 100.0;
        double expectedEma21 = 100.0;
        double alpha9 = 2.0 / 10.0;
        double alpha21 = 2.0 / 22.0;

        for (int i = 0; i < 40; i++) {
            double close = 100.0 + i * 0.75;
            double open = close - 0.30;
            double high = close + 0.45;
            double low = open - 0.25;
            double vol = 5000.0 + i * 100.0;
            if (i > 0) {
                expectedEma9 = (close - expectedEma9) * alpha9 + expectedEma9;
                expectedEma21 = (close - expectedEma21) * alpha21 + expectedEma21;
            }
            state.step(open, high, low, close, vol, false);
        }

        IndicatorSnapshotDto snap = state.toSnapshot(Symbol.of("EURUSD"), Timeframe.M15, 39, Instant.now());
        assertThat(snap.ema9()).isCloseTo(expectedEma9, within(1e-6));
        assertThat(snap.ema21()).isCloseTo(expectedEma21, within(1e-6));
        assertThat(snap.ema9()).isGreaterThan(snap.ema21());
        assertThat(snap.ema21()).isGreaterThan(snap.ema50());
        assertThat(snap.supertrendBullish()).isTrue();
        assertThat(snap.psarBullish()).isTrue();
        assertThat(snap.adx14()).isGreaterThan(25.0);
        assertThat(snap.plusDi14()).isGreaterThan(snap.minusDi14());

        // Micro-benchmark: verify < 5 µs / indicator / bar on hot path (40 indicators per step)
        for (int i = 0; i < 4000; i++) {
            state.step(110.0, 111.0, 109.5, 110.5, 1000.0, false);
        }
        int iterations = 10_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            double c = 110.0 + (i % 15) * 0.1;
            state.step(c - 0.1, c + 0.2, c - 0.2, c, 1500.0, false);
        }
        double microsPerSuiteStep = ((System.nanoTime() - t0) / 1000.0) / iterations;
        double microsPerIndicator = microsPerSuiteStep / 40.0;
        assertThat(microsPerIndicator).isLessThan(5.0);
    }

    @Test
    void momentumAndDivergenceDetectorProduceAccurateGoldenValues() {
        IncrementalIndicatorState state = new IncrementalIndicatorState();
        // Strong uptrend followed by price dip with higher RSI support
        for (int i = 0; i < 25; i++) {
            double c = 50.0 + i * 1.2;
            state.step(c - 0.4, c + 0.6, c - 0.5, c, 2000.0, false);
        }
        IndicatorSnapshotDto bullSnap = state.toSnapshot(Symbol.of("THYAO"), Timeframe.H1, 24, Instant.now());
        assertThat(bullSnap.rsi14()).isGreaterThan(75.0);
        assertThat(bullSnap.macdLine()).isGreaterThan(0.0);
        assertThat(bullSnap.macdHistogram()).isGreaterThan(0.0);
        assertThat(bullSnap.cci20()).isGreaterThan(50.0);
        assertThat(bullSnap.williamsR14()).isBetween(-25.0, 0.0);
        assertThat(bullSnap.momentum10()).isGreaterThan(0.0);
        assertThat(bullSnap.awesomeOscillator()).isGreaterThan(0.0);
        assertThat(bullSnap.ultimateOscillator()).isBetween(50.0, 100.0);

        // Induce bearish divergence: higher close than 5 bars ago, but weaker momentum/RSI
        for (int i = 0; i < 4; i++) {
            double c = 79.0 - i * 0.8;
            state.step(c + 0.2, c + 0.4, c - 0.4, c, 2000.0, false);
        }
        // Spike close slightly above 5 bars ago
        state.step(77.0, 81.0, 76.8, 80.5, 2000.0, false);
        IndicatorSnapshotDto divSnap = state.toSnapshot(Symbol.of("THYAO"), Timeframe.H1, 29, Instant.now());
        assertThat(divSnap.rsiDivergence()).isEqualTo(DivergenceType.BEARISH);
    }

    @Test
    void volatilityAndVolumeResetVwapOnNewSessionAndComputeVolumeProfile() {
        IncrementalIndicatorState state = new IncrementalIndicatorState();
        state.step(100.0, 102.0, 99.0, 101.0, 1000.0, true);
        state.step(101.0, 105.0, 100.0, 104.0, 3000.0, false);
        IndicatorSnapshotDto day1 = state.toSnapshot(Symbol.of("AAPL"), Timeframe.M15, 1, Instant.now());
        assertThat(day1.vwap()).isGreaterThan(101.0);
        assertThat(day1.obv()).isEqualTo(3000.0);

        // New trading session resets VWAP exactly to new bar's typical price
        double open = 120.0, high = 123.0, low = 119.0, close = 121.0;
        double expectedTypical = (high + low + close) / 3.0;
        state.step(open, high, low, close, 500.0, true);
        IndicatorSnapshotDto day2 = state.toSnapshot(Symbol.of("AAPL"), Timeframe.M15, 2, Instant.now());
        assertThat(day2.vwap()).isCloseTo(expectedTypical, within(1e-6));
        assertThat(day2.volumeProfilePoc()).isBetween(99.0, 123.0);
    }

    @Test
    void pivotLevelsCalculateClassicFibonacciCamarillaAndSnapStopLossAndTakeProfit() {
        IncrementalIndicatorState state = new IncrementalIndicatorState();
        state.step(100.0, 110.0, 90.0, 105.0, 1000.0, false);
        IndicatorSnapshotDto snap = state.toSnapshot(Symbol.of("XAUUSD"), Timeframe.H4, 0, Instant.now());
        PivotLevels p = snap.pivots();

        // P = (110 + 90 + 105)/3 = 101.666667
        assertThat(p.pivot()).isCloseTo(305.0 / 3.0, within(1e-6));
        assertThat(p.classicR1()).isGreaterThan(p.pivot());
        assertThat(p.classicS1()).isLessThan(p.pivot());
        assertThat(p.fibR1()).isCloseTo(p.pivot() + 0.382 * 20.0, within(1e-6));
        assertThat(p.camR3()).isCloseTo(105.0 + 20.0 * 1.1 / 4.0, within(1e-6));

        // Snapping stop loss near classicS1 (93.333)
        double snappedSl = p.snapStopLoss(Direction.BUY, 105.0, 93.5, 4.0);
        assertThat(snappedSl).isCloseTo(p.classicS1() - 0.2, within(1e-4));

        // Snapping take profit near classicR1 (113.333)
        double snappedTp = p.snapTakeProfit(Direction.BUY, 105.0, 113.1, 4.0);
        assertThat(snappedTp).isCloseTo(p.classicR1(), within(1e-4));
    }

    @Test
    void regimeClassifierAppliesHysteresisAndPublishesEventOnChange() {
        RegimeClassifier classifier = new RegimeClassifier();
        assertThat(classifier.currentRegime()).isEqualTo(MarketRegime.RANGING);

        // ADX 23 (< 25 entry) stays RANGING
        assertThat(classifier.classify(23.0, 30.0, 15.0, 1.0, 0.02, 105.0, 100.0))
                .isEqualTo(MarketRegime.RANGING);

        // ADX 27 (>= 25 entry) transitions to TRENDING_UP
        assertThat(classifier.classify(27.0, 30.0, 15.0, 1.0, 0.02, 105.0, 100.0))
                .isEqualTo(MarketRegime.TRENDING_UP);

        // Hysteresis: ADX drops to 22.0 (>= 20 exit) -> remains TRENDING_UP without flickering!
        assertThat(classifier.classify(22.0, 28.0, 16.0, 1.0, 0.02, 105.0, 100.0))
                .isEqualTo(MarketRegime.TRENDING_UP);

        // ADX drops to 18.5 (< 20 exit) -> transitions back to RANGING
        assertThat(classifier.classify(18.5, 20.0, 19.0, 1.0, 0.02, 101.0, 100.0))
                .isEqualTo(MarketRegime.RANGING);

        // Service integration event check
        List<Object> events = new ArrayList<>();
        TechnicalAnalysisService service = new TechnicalAnalysisService(events::add);
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 30; i++) {
            double c = 100.0 + i * 2.5;
            service.onBarClosed(new BarClosed(
                    Symbol.of("EURUSD"), Timeframe.H1, i,
                    t.plusSeconds(i * 3600L), t.plusSeconds((i + 1) * 3600L),
                    c - 0.5, c + 1.0, c - 0.8, c, 2000.0, i == 0
            ));
        }
        assertThat(events).anyMatch(MarketRegimeChanged.class::isInstance);
    }
}

