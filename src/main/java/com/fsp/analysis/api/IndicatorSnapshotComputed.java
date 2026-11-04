package com.fsp.analysis.api;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record IndicatorSnapshotComputed(
        IndicatorSnapshotDto snapshot
) {}

