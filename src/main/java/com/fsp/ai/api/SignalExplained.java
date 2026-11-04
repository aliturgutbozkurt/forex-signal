package com.fsp.ai.api;

import java.time.Instant;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record SignalExplained(
        String signalId,
        String explanationTr,
        double faithfulnessScore,
        Instant explainedAt
) {}

