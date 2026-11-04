package com.fsp.shared;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum Currency {
    USD("USD", "$", "dolar", "dollars", 5),
    EUR("EUR", "€", "euro", "euros", 5),
    TRY("TRY", "₺", "lira", "lira", 2),
    GBP("GBP", "£", "sterlin", "pounds", 5),
    JPY("JPY", "¥", "yen", "yen", 3),
    XAU("XAU", "oz", "ons dolar", "dollars per ounce", 2),
    PTS("PTS", "puan", "puan", "points", 2);

    private final String code;
    private final String symbol;
    private final String spokenNameTr;
    private final String spokenNameEn;
    private final int defaultScale;

    Currency(String code, String symbol, String spokenNameTr, String spokenNameEn, int defaultScale) {
        this.code = Objects.requireNonNull(code);
        this.symbol = Objects.requireNonNull(symbol);
        this.spokenNameTr = Objects.requireNonNull(spokenNameTr);
        this.spokenNameEn = Objects.requireNonNull(spokenNameEn);
        this.defaultScale = defaultScale;
    }

    public String code() {
        return code;
    }

    public String symbol() {
        return symbol;
    }

    public String spokenNameTr() {
        return spokenNameTr;
    }

    public String spokenNameEn() {
        return spokenNameEn;
    }

    public String spokenName(String language) {
        return "en".equalsIgnoreCase(language) ? spokenNameEn : spokenNameTr;
    }

    public int defaultScale() {
        return defaultScale;
    }
}

