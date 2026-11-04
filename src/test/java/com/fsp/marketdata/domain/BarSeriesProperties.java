package com.fsp.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based verification of {@link BarSeries} against {@code docs/tla/BarIngestion.tla}.
 * Verifies INV-BAR-1 (OnlyClosedBars), INV-BAR-2 (StrictlyOrderedNoGaps), and LIV-BAR-1 (EveryClosedBarEvaluated).
 */
@Tag("INV-BAR-1")
@Tag("INV-BAR-2")
@Tag("LIV-BAR-1")
class BarSeriesProperties {

    private static final Symbol EURUSD = Symbol.of("EURUSD");
    private static final Instant ANCHOR = Instant.parse("2026-01-01T00:00:00Z");

    @Property
    void randomOutOfOrderDuplicateAndGappedStreamMaintainsNoRepaintAndStrictOrder(
            @ForAll @IntRange(min = 5, max = 35) int nowBarIndex,
            @ForAll @Size(min = 1, max = 60) List<@IntRange(min = 0, max = 45) Integer> arrivalIndices,
            @ForAll @Size(min = 0, max = 10) List<@IntRange(min = 0, max = 45) Integer> formingArrivalIndices
    ) {
        BarSeries series = new BarSeries(EURUSD, Timeframe.M5, 128);
        series.advanceClockTo(nowBarIndex);

        // 1. Poll arbitrary out-of-order and duplicate closed bars + forming bars
        for (int idx : arrivalIndices) {
            boolean closed = idx < nowBarIndex;
            Bar candidate = makeBar(idx, closed);
            boolean accepted = series.receive(candidate);
            if (idx >= nowBarIndex) {
                // INV-BAR-1: Forming bars are never accepted
                assertThat(accepted).isFalse();
            }
            series.drainEvaluatableBars();
            assertInvariants(series, nowBarIndex);
        }

        // Explicitly send forming bars even with index < nowBarIndex
        for (int idx : formingArrivalIndices) {
            Bar forming = makeBar(idx, false);
            assertThat(series.receive(forming)).isFalse();
        }

        // 2. Backfill any missing gaps below nowBarIndex (LIV-BAR-1)
        OptionalLong missing = series.missingWatermarkIndex();
        while (missing.isPresent()) {
            long gapIdx = missing.getAsLong();
            series.receive(makeBar(gapIdx, true));
            series.drainEvaluatableBars();
            assertInvariants(series, nowBarIndex);
            missing = series.missingWatermarkIndex();
        }

        // LIV-BAR-1: All closed bars [0 .. nowBarIndex - 1] have been evaluated exactly once in order
        assertThat(series.watermark()).isEqualTo(nowBarIndex);
        List<Long> expectedOrder = new ArrayList<>(nowBarIndex);
        for (long i = 0; i < nowBarIndex; i++) {
            expectedOrder.add(i);
        }
        assertThat(series.evaluationLog()).containsExactlyElementsOf(expectedOrder);
    }

    private static void assertInvariants(BarSeries series, long nowBarIndex) {
        List<Long> log = series.evaluationLog();
        for (int i = 0; i < log.size(); i++) {
            long barIdx = log.get(i);
            // INV-BAR-1: OnlyClosedBars (evalLog[i] < now)
            assertThat(barIdx).isLessThan(nowBarIndex);
            // INV-BAR-2: StrictlyOrderedNoGaps (evalLog[i] = i)
            assertThat(barIdx).isEqualTo(i);
        }
    }

    private static Bar makeBar(long idx, boolean closed) {
        Instant open = ANCHOR.plusSeconds(idx * 300);
        Instant close = open.plusSeconds(300);
        double base = 1.0800 + (idx * 0.0001);
        return new Bar(EURUSD, Timeframe.M5, idx, open, close, base, base + 0.0010, base - 0.0008, base + 0.0004, 1000.0, closed);
    }
}

