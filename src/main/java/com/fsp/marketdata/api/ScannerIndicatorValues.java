package com.fsp.marketdata.api;

public record ScannerIndicatorValues(
        double recommendAll,
        double recommendMa,
        double recommendOther,
        double rsi14,
        double adx14,
        boolean available
) {
    public static ScannerIndicatorValues unavailable() {
        return new ScannerIndicatorValues(0.0, 0.0, 0.0, 50.0, 20.0, false);
    }
}

