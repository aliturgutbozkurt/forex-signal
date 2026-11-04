package com.fsp.watchlist.domain;

import java.time.LocalTime;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record VoicePreference(
        boolean enabled,
        String locale,
        String buyVoiceName,
        String sellVoiceName,
        double speechRate,
        double buyPitch,
        double sellPitch,
        boolean quietHoursEnabled,
        LocalTime quietFrom,
        LocalTime quietTo
) {

    public VoicePreference {
        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(buyVoiceName, "buyVoiceName must not be null");
        Objects.requireNonNull(sellVoiceName, "sellVoiceName must not be null");
        Objects.requireNonNull(quietFrom, "quietFrom must not be null");
        Objects.requireNonNull(quietTo, "quietTo must not be null");
        if (speechRate < 0.5 || speechRate > 2.0) {
            throw new IllegalArgumentException("speechRate must be in [0.5, 2.0]: " + speechRate);
        }
        if (buyPitch < 0.5 || buyPitch > 2.0 || sellPitch < 0.5 || sellPitch > 2.0) {
            throw new IllegalArgumentException("pitch must be in [0.5, 2.0]");
        }
    }

    public static VoicePreference defaultTurkish() {
        return new VoicePreference(
                true,
                "tr-TR",
                "Yelda (tr-TR, Kadın — AL)",
                "Cem (tr-TR, Erkek — SAT)",
                1.05,
                1.15,
                0.88,
                false,
                LocalTime.of(23, 0),
                LocalTime.of(7, 0)
        );
    }

    public boolean isQuietAt(LocalTime time) {
        if (!quietHoursEnabled) {
            return false;
        }
        if (!quietFrom.isAfter(quietTo)) {
            return !time.isBefore(quietFrom) && !time.isAfter(quietTo);
        }
        return !time.isBefore(quietFrom) || !time.isAfter(quietTo);
    }
}

