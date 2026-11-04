package com.fsp.shared;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum MarketRegime {
    TRENDING_UP("Yükselen Trend"),
    TRENDING_DOWN("Düşen Trend"),
    RANGING("Yatay Piyasa"),
    VOLATILE("Yüksek Volatilite");

    private final String labelTr;

    MarketRegime(String labelTr) {
        this.labelTr = labelTr;
    }

    public String labelTr() {
        return labelTr;
    }

    public boolean isTrending() {
        return this == TRENDING_UP || this == TRENDING_DOWN;
    }
}

