package com.fsp.shared;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Symbol(String value) {

    private static final Pattern VALID_SYMBOL = Pattern.compile("^[A-Z0-9._-]{2,16}$");

    public Symbol {
        Objects.requireNonNull(value, "symbol value must not be null");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_SYMBOL.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid symbol format: " + value);
        }
    }

    public static Symbol of(String value) {
        return new Symbol(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

