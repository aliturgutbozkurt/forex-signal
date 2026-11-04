package com.fsp.alerting.domain;

import com.fsp.shared.Currency;
import com.fsp.shared.Direction;
import com.fsp.shared.Price;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pure domain factory generating localized spoken text, tone profiles, and voice profiles
 * for BUY (AL) and SELL (SAT) signals (SPEC-001 FR-08, ADR-0003, Issue #26).
 */
public final class VoicePayloadFactory {

    private static final Map<Character, String> TR_DIGIT_WORDS = Map.of(
            '0', "sıfır",
            '1', "bir",
            '2', "iki",
            '3', "üç",
            '4', "dört",
            '5', "beş",
            '6', "altı",
            '7', "yedi",
            '8', "sekiz",
            '9', "dokuz"
    );

    public record VoiceNotificationPayload(
            String signalId,
            String symbol,
            String timeframe,
            String direction,
            String directionLabel,
            String formattedPrice,
            String currencyUnitName,
            String spokenText,
            String spokenPhoneticTr,
            String chimePattern,
            int primaryFreqHz,
            int secondaryFreqHz,
            String voiceProfileName,
            double speechRate,
            double pitch
    ) {}

    public VoiceNotificationPayload createPayload(
            String signalId,
            Symbol symbol,
            Timeframe timeframe,
            Direction direction,
            Price entryPrice,
            String localeTag,
            String buyVoiceName,
            String sellVoiceName,
            double speechRate,
            double buyPitch,
            double sellPitch
    ) {
        Objects.requireNonNull(signalId);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(timeframe);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(entryPrice);

        boolean english = localeTag != null && localeTag.toLowerCase(Locale.ROOT).startsWith("en");
        Currency currency = entryPrice.currency();
        String formattedNumber = formatLocalizedNumber(entryPrice.amount(), currency, english);
        String unitName = currency.spokenName(english ? "en" : "tr");
        String dirLabel = direction.label(english ? "en" : "tr");

        String spokenText;
        if (english) {
            spokenText = String.format(Locale.US,
                    "%s, %s signal, price %s %s",
                    symbol.value(), dirLabel, formattedNumber, unitName);
        } else {
            spokenText = String.format(Locale.forLanguageTag("tr-TR"),
                    "%s, %s sinyali, fiyat %s %s",
                    symbol.value(), dirLabel, formattedNumber, unitName);
        }

        String phoneticTr = String.format(Locale.forLanguageTag("tr-TR"),
                "%s, %s sinyali, fiyat %s %s",
                symbol.value(), direction.labelTr(), toPhoneticTurkishDecimal(formattedNumber), currency.spokenNameTr());

        boolean isBuy = direction == Direction.BUY;
        String chime = isBuy ? "ASCENDING_DUAL_CHIME" : "DESCENDING_DUAL_CHIME";
        int freq1 = isBuy ? 587 : 659; // D5 -> A5 ascending for AL; E5 -> A4 descending for SAT
        int freq2 = isBuy ? 880 : 440;
        String selectedVoice = isBuy ? buyVoiceName : sellVoiceName;
        double selectedPitch = isBuy ? buyPitch : sellPitch;

        return new VoiceNotificationPayload(
                signalId,
                symbol.value(),
                timeframe.label(),
                direction.name(),
                dirLabel,
                formattedNumber,
                unitName,
                spokenText,
                phoneticTr,
                chime,
                freq1,
                freq2,
                selectedVoice,
                speechRate,
                selectedPitch
        );
    }

    private static String formatLocalizedNumber(BigDecimal amount, Currency currency, boolean english) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(
                english ? Locale.US : Locale.forLanguageTag("tr-TR"));
        int scale = Math.max(amount.scale(), currency == Currency.TRY ? 2 : 4);
        scale = Math.min(scale, currency.defaultScale());
        StringBuilder pattern = new StringBuilder("0");
        if (scale > 0) {
            pattern.append('.');
            pattern.repeat('0', Math.min(scale, 2));
            if (scale > 2) {
                pattern.repeat('#', scale - 2);
            }
        }
        DecimalFormat df = new DecimalFormat(pattern.toString(), symbols);
        return df.format(amount);
    }

    private static String toPhoneticTurkishDecimal(String trFormatted) {
        int commaIdx = trFormatted.indexOf(',');
        if (commaIdx < 0) {
            return trFormatted;
        }
        String intPart = trFormatted.substring(0, commaIdx);
        String fracPart = trFormatted.substring(commaIdx + 1);
        if (fracPart.length() <= 2) {
            return trFormatted;
        }
        // For 4-5 decimal Forex prices like 1,0845 -> "bir virgül sıfır sekiz dört beş"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intPart.length(); i++) {
            char c = intPart.charAt(i);
            sb.append(TR_DIGIT_WORDS.getOrDefault(c, String.valueOf(c))).append(' ');
        }
        sb.append("virgül");
        for (int i = 0; i < fracPart.length(); i++) {
            char c = fracPart.charAt(i);
            sb.append(' ').append(TR_DIGIT_WORDS.getOrDefault(c, String.valueOf(c)));
        }
        return sb.toString().trim();
    }
}

