package com.fsp.watchlist.api;

import java.time.LocalTime;

public record VoicePreferenceDto(
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
) {}

