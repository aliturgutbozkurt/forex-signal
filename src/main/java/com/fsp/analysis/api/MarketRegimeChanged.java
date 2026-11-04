package com.fsp.analysis.api;

import com.fsp.shared.MarketRegime;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record MarketRegimeChanged(
        Symbol symbol,
        Timeframe timeframe,
        MarketRegime previousRegime,
        MarketRegime currentRegime,
        Instant occurredAt
) {}

