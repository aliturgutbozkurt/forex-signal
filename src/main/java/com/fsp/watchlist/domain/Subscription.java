package com.fsp.watchlist.domain;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Subscription(Symbol symbol, Set<Timeframe> timeframes) {

    public Subscription {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeframes, "timeframes must not be null");
        if (timeframes.isEmpty()) {
            throw new IllegalArgumentException("Subscription timeframes must not be empty");
        }
        timeframes = Set.copyOf(timeframes);
    }
}

