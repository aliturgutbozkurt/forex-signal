package com.fsp.analysis.domain;

/**
 * Per-(Symbol, Timeframe) watermark tracker in the Technical Analysis bounded context
 * guaranteeing that bars are processed strictly monotonically without duplicates (INV-BAR-2).
 */
public final class BarWatermark {

    private long lastProcessedIndex = -1L;

    public boolean tryAdvance(long barIndex) {
        if (barIndex <= lastProcessedIndex) {
            return false;
        }
        lastProcessedIndex = barIndex;
        return true;
    }

    public long lastProcessedIndex() {
        return lastProcessedIndex;
    }
}

