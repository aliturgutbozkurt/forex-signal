package com.fsp.marketdata.domain;

import com.fsp.shared.Symbol;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record InstrumentId(Symbol symbol, String exchange) {

    public InstrumentId {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(exchange, "exchange must not be null");
        exchange = exchange.trim().toUpperCase();
        if (exchange.isEmpty()) {
            throw new IllegalArgumentException("exchange must not be blank");
        }
    }

    public static InstrumentId of(String exchange, String ticker) {
        return new InstrumentId(Symbol.of(ticker), exchange);
    }

    public String fullTicker() {
        return exchange + ":" + symbol.value();
    }

    @Override
    public String toString() {
        return fullTicker();
    }
}

