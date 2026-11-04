package com.fsp.signal.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum SignalState {
    ACTIVE(false, "Aktif"),
    TP_HIT(true, "Hedef (TP) Göründü"),
    SL_HIT(true, "Stop (SL) Oldu"),
    EXPIRED(true, "Süresi Doldu"),
    INVALIDATED(true, "Ters Sinyalle İptal");

    private final boolean terminal;
    private final String labelTr;

    SignalState(boolean terminal, String labelTr) {
        this.terminal = terminal;
        this.labelTr = labelTr;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public String labelTr() {
        return labelTr;
    }
}

