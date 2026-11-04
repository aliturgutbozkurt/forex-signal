package com.fsp.signal.api;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record SignalInvalidated(
        SignalDto signal,
        String replacedBySignalId
) {}

