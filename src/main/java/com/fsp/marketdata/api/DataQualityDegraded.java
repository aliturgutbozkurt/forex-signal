package com.fsp.marketdata.api;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record DataQualityDegraded(
        Symbol symbol,
        Timeframe timeframe,
        double divergencePct,
        Instant occurredAt
) {}

