package com.fsp.marketdata.domain;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Bar(
        Symbol symbol,
        Timeframe timeframe,
        long barIndex,
        Instant openTime,
        Instant closeTime,
        double open,
        double high,
        double low,
        double close,
        double volume,
        boolean closed
) {

    public Bar {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(openTime, "openTime must not be null");
        Objects.requireNonNull(closeTime, "closeTime must not be null");
        if (barIndex < 0) {
            throw new IllegalArgumentException("barIndex must be non-negative: " + barIndex);
        }
        if (!closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("closeTime must be strictly after openTime");
        }
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            throw new IllegalArgumentException("OHLC prices must be positive");
        }
        if (high < Math.max(open, close) || low > Math.min(open, close) || high < low) {
            throw new IllegalArgumentException("Invalid OHLC envelope: H=" + high + ", L=" + low);
        }
        if (volume < 0 || !Double.isFinite(volume)) {
            throw new IllegalArgumentException("volume must be finite and non-negative");
        }
    }

    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }

    public double range() {
        return high - low;
    }

    public boolean isBullish() {
        return close >= open;
    }
}

