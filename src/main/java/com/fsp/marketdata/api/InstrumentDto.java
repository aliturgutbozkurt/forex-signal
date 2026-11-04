package com.fsp.marketdata.api;

import com.fsp.shared.AssetClass;
import com.fsp.shared.Currency;
import com.fsp.shared.Symbol;
import java.math.BigDecimal;
import java.time.ZoneId;

public record InstrumentDto(
        Symbol symbol,
        String exchange,
        String displayName,
        AssetClass assetClass,
        Currency quoteCurrency,
        BigDecimal tickSize,
        ZoneId timezone,
        boolean delayedFeed,
        boolean sessionOpen
) {
    public String tradingViewSymbol() {
        return switch (symbol.value()) {
            case "SPX500" -> "FOREXCOM:SPXUSD";
            case "NDX100" -> "NASDAQ:NDX";
            default -> exchange + ":" + symbol.value();
        };
    }
}

