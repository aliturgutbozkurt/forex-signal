package com.fsp.marketdata.domain;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.jmolecules.ddd.annotation.AggregateRoot;

/**
 * Primitive ring-buffer backed BarSeries aggregate enforcing TLA+ {@code BarIngestion.tla} invariants:
 * <ul>
 *   <li><b>INV-BAR-1 (OnlyClosedBars):</b> Forming bars ({@code !bar.closed()} or {@code bar.barIndex() >= nowIndex}) are never evaluated.</li>
 *   <li><b>INV-BAR-2 (StrictlyOrderedNoGaps):</b> Evaluated bars strictly follow {@code evalLog[i] = i - 1} via {@code watermark}.</li>
 *   <li><b>LIV-BAR-1 (EveryClosedBarEvaluated):</b> Missing bars below {@code nowIndex} are surfaced for deterministic backfill.</li>
 * </ul>
 */
@AggregateRoot
public final class BarSeries {

    private final Symbol symbol;
    private final Timeframe timeframe;
    private final int capacity;

    // Primitive ring buffers for zero-allocation hot path access
    private final long[] barIndices;
    private final long[] openEpochMillis;
    private final long[] closeEpochMillis;
    private final double[] opens;
    private final double[] highs;
    private final double[] lows;
    private final double[] closes;
    private final double[] volumes;

    private int head = 0;
    private int size = 0;

    // TLA+ BarIngestion state variables
    private long nowIndex = 0;
    private long watermark = 0;
    private final Map<Long, Bar> pendingReceived = new HashMap<>();
    private final List<Long> evalLog = new ArrayList<>();

    public BarSeries(Symbol symbol, Timeframe timeframe, int capacity) {
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.timeframe = Objects.requireNonNull(timeframe, "timeframe must not be null");
        if (capacity < 8) {
            throw new IllegalArgumentException("capacity must be at least 8");
        }
        this.capacity = capacity;
        this.barIndices = new long[capacity];
        this.openEpochMillis = new long[capacity];
        this.closeEpochMillis = new long[capacity];
        this.opens = new double[capacity];
        this.highs = new double[capacity];
        this.lows = new double[capacity];
        this.closes = new double[capacity];
        this.volumes = new double[capacity];
    }

    public Symbol symbol() {
        return symbol;
    }

    public Timeframe timeframe() {
        return timeframe;
    }

    public long nowIndex() {
        return nowIndex;
    }

    public long watermark() {
        return watermark;
    }

    public int size() {
        return size;
    }

    public List<Long> evaluationLog() {
        return Collections.unmodifiableList(evalLog);
    }

    /**
     * TLA+ {@code Advance}: advances the current forming bar index.
     * Any bar with index {@code < nowIndex} is considered closed.
     */
    public void advanceClockTo(long newNowIndex) {
        if (newNowIndex < this.nowIndex) {
            throw new IllegalArgumentException(
                    "Clock cannot move backwards: " + newNowIndex + " < " + this.nowIndex);
        }
        this.nowIndex = newNowIndex;
    }

    /**
     * TLA+ {@code Poll} / {@code Backfill}: receives a candidate bar from an adapter.
     * Enforces INV-BAR-1: forming bars ({@code !bar.closed()} or {@code barIndex >= nowIndex}) are rejected.
     * Duplicate or already-evaluated bars ({@code barIndex < watermark}) are ignored idempotently.
     */
    public boolean receive(Bar bar) {
        Objects.requireNonNull(bar, "bar must not be null");
        if (!bar.symbol().equals(symbol) || bar.timeframe() != timeframe) {
            throw new IllegalArgumentException("Bar symbol/timeframe mismatch");
        }
        if (!bar.closed() || bar.barIndex() >= nowIndex) {
            // INV-BAR-1: Never ingest forming bars
            return false;
        }
        if (bar.barIndex() < watermark) {
            // Already evaluated (idempotent deduplication)
            return false;
        }
        pendingReceived.putIfAbsent(bar.barIndex(), bar);
        return true;
    }

    /**
     * TLA+ {@code Evaluate}: evaluates all contiguous closed bars starting from {@code watermark}.
     * Guarantees INV-BAR-2: strictly ordered, gap-free, single evaluation per bar.
     */
    public List<Bar> drainEvaluatableBars() {
        List<Bar> ready = new ArrayList<>();
        while (watermark < nowIndex && pendingReceived.containsKey(watermark)) {
            Bar next = pendingReceived.remove(watermark);
            appendToRing(next);
            evalLog.add(watermark);
            ready.add(next);
            watermark++;
        }
        return ready;
    }

    /**
     * Identifies if a gap prevents {@code watermark} from advancing (triggers TLA+ {@code Backfill}).
     */
    public OptionalLong missingWatermarkIndex() {
        if (watermark < nowIndex && !pendingReceived.containsKey(watermark)) {
            return OptionalLong.of(watermark);
        }
        return OptionalLong.empty();
    }

    public Optional<Bar> latestEvaluatedBar() {
        if (size == 0) {
            return Optional.empty();
        }
        int lastSlot = (head - 1 + capacity) % capacity;
        return Optional.of(slotToBar(lastSlot));
    }

    public double closeAtOffsetFromLatest(int offset) {
        if (offset < 0 || offset >= size) {
            throw new IndexOutOfBoundsException("Offset " + offset + " out of bounds for size " + size);
        }
        int slot = (head - 1 - offset + capacity * 2) % capacity;
        return closes[slot];
    }

    private void appendToRing(Bar bar) {
        barIndices[head] = bar.barIndex();
        openEpochMillis[head] = bar.openTime().toEpochMilli();
        closeEpochMillis[head] = bar.closeTime().toEpochMilli();
        opens[head] = bar.open();
        highs[head] = bar.high();
        lows[head] = bar.low();
        closes[head] = bar.close();
        volumes[head] = bar.volume();
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    private Bar slotToBar(int slot) {
        return new Bar(
                symbol,
                timeframe,
                barIndices[slot],
                Instant.ofEpochMilli(openEpochMillis[slot]),
                Instant.ofEpochMilli(closeEpochMillis[slot]),
                opens[slot],
                highs[slot],
                lows[slot],
                closes[slot],
                volumes[slot],
                true
        );
    }
}

