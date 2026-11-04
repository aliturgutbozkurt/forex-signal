package com.fsp.alerting.api;

import com.fsp.shared.Direction;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record AlertPayloadDto(
        String signalId,
        Symbol symbol,
        Timeframe timeframe,
        Direction direction,
        String formattedPrice,
        String currencyUnitName,
        String spokenText,
        String spokenPhoneticTr,
        String chimePattern,
        int primaryFreqHz,
        int secondaryFreqHz,
        String voiceProfileName,
        double speechRate,
        double pitch,
        int confidence,
        double confluenceScore,
        @Nullable String explanationTr,
        Instant publishedAt
) {}

