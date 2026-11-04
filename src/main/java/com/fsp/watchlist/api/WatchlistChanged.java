package com.fsp.watchlist.api;

import java.time.Instant;
import java.util.List;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record WatchlistChanged(
        String userId,
        List<SubscriptionDto> subscriptions,
        Instant changedAt
) {}

