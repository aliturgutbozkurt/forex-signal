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
            case "NATGAS" -> "OANDA:NATGASUSD";
            case "GASOLINE" -> "NYMEX:RB1!";
            case "HEATOIL" -> "NYMEX:HO1!";
            case "COPPER" -> "OANDA:XCUUSD";
            case "ALUMINUM" -> "COMEX:ALI1!";
            case "WHEAT" -> "OANDA:WHEATUSD";
            case "CORN" -> "OANDA:CORNUSD";
            case "SOYBN" -> "OANDA:SOYBNUSD";
            case "SUGAR" -> "OANDA:SUGARUSD";
            case "COFFEE" -> "ICEUS:KC1!";
            case "COCOA" -> "ICEUS:CC1!";
            case "COTTON" -> "ICEUS:CT1!";
            case "SPX500" -> "FOREXCOM:SPXUSD";
            case "NDX100" -> "NASDAQ:NDX";
            default -> exchange + ":" + symbol.value();
        };
    }
}

