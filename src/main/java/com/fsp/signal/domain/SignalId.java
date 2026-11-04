package com.fsp.signal.domain;

import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record SignalId(String value) {

    public SignalId {
        Objects.requireNonNull(value, "SignalId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SignalId must not be blank");
        }
    }

    public static SignalId random() {
        return new SignalId("SIG-" + UUID.randomUUID());
    }

    public static SignalId of(String value) {
        return new SignalId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

