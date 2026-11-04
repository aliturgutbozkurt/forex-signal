package com.fsp.signal.api;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record TakeProfitHit(
        SignalDto signal,
        int tpLevel
) {}

