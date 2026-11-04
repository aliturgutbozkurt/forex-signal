package com.fsp.marketdata.api;

import java.time.Instant;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record DataSourceDegraded(
        String sourceName,
        String reason,
        Instant occurredAt
) {}

