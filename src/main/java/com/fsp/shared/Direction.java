package com.fsp.shared;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum Direction {
    BUY("AL", "BUY"),
    SELL("SAT", "SELL");

    private final String labelTr;
    private final String labelEn;

    Direction(String labelTr, String labelEn) {
        this.labelTr = labelTr;
        this.labelEn = labelEn;
    }

    public Direction opposite() {
        return this == BUY ? SELL : BUY;
    }

    public int sign() {
        return this == BUY ? 1 : -1;
    }

    public String labelTr() {
        return labelTr;
    }

    public String labelEn() {
        return labelEn;
    }

    public String label(String language) {
        return "en".equalsIgnoreCase(language) ? labelEn : labelTr;
    }
}

