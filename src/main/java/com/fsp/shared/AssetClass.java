package com.fsp.shared;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum AssetClass {
    FOREX("Forex", "forex"),
    US_EQUITY("ABD Hisseleri", "america"),
    BIST_EQUITY("BIST Hisseleri", "turkey"),
    COMMODITY("Emtia", "cfd"),
    INDEX("Endeks", "cfd");

    private final String displayNameTr;
    private final String scannerMarket;

    AssetClass(String displayNameTr, String scannerMarket) {
        this.displayNameTr = displayNameTr;
        this.scannerMarket = scannerMarket;
    }

    public String displayNameTr() {
        return displayNameTr;
    }

    public String scannerMarket() {
        return scannerMarket;
    }
}

