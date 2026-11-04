package com.fsp.signal.domain;

public final class SignalWindowClosedException extends IllegalStateException {

    public SignalWindowClosedException(String message) {
        super(message);
    }
}

