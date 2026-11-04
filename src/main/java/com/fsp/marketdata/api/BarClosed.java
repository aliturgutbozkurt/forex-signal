package com.fsp.marketdata.api;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record BarClosed(
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
        boolean newSessionDay
) {}

