package com.fsp.signal.api;

import com.fsp.shared.Currency;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Price;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.shared.Vote;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record SignalDto(
        String signalId,
        Symbol symbol,
        Timeframe timeframe,
        Direction direction,
        Price entry,
        Price stopLoss,
        Price tp1,
        Price tp2,
        Price tp3,
        double riskRewardTp1,
        int confidence,
        double confluenceScore,
        MarketRegime regime,
        List<Vote> rationale,
        String state,
        String stateLabelTr,
        String strategyId,
        int strategyVersion,
        long openedAtBar,
        Instant openedAt,
        @Nullable Instant closedAt,
        @Nullable String explanation
) {
    public Currency currency() {
        return entry.currency();
    }
}

