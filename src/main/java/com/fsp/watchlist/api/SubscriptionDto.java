package com.fsp.watchlist.api;

import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.Set;

public record SubscriptionDto(
        Symbol symbol,
        Set<Timeframe> timeframes
) {}

