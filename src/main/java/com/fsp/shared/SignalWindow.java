package com.fsp.shared;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * User-defined time/session window during which signal generation is permitted (SPEC-001 §5.1, §6.2, FR-03).
 * Handles timezone conversion and DST transitions via {@link ZoneId}.
 */
@ValueObject
public record SignalWindow(
        String label,
        Set<DayOfWeek> days,
        LocalTime from,
        LocalTime to,
        ZoneId zoneId
) {

    public SignalWindow {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(days, "days must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("SignalWindow days must not be empty");
        }
        days = Set.copyOf(days);
    }

    public static SignalWindow bistSession() {
        return new SignalWindow(
                "BIST Seansı (10:00–18:00)",
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                ZoneId.of("Europe/Istanbul")
        );
    }

    public static SignalWindow londonNewYorkOverlap() {
        return new SignalWindow(
                "Londra + NY Overlap (13:00–17:00 UTC)",
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                LocalTime.of(13, 0),
                LocalTime.of(17, 0),
                ZoneId.of("UTC")
        );
    }

    public static SignalWindow usRegularSession() {
        return new SignalWindow(
                "ABD Seansı (09:30–16:00 NY)",
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                LocalTime.of(9, 30),
                LocalTime.of(16, 0),
                ZoneId.of("America/New_York")
        );
    }

    public static SignalWindow alwaysOpen() {
        return new SignalWindow(
                "7/24 Tüm Saatler",
                EnumSet.allOf(DayOfWeek.class),
                LocalTime.MIN,
                LocalTime.MAX,
                ZoneId.of("UTC")
        );
    }

    public boolean isAllowedAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        ZonedDateTime zdt = instant.atZone(zoneId);
        if (!days.contains(zdt.getDayOfWeek())) {
            return false;
        }
        LocalTime localTime = zdt.toLocalTime();
        if (!from.isAfter(to)) {
            return !localTime.isBefore(from) && !localTime.isAfter(to);
        } else {
            // Overnight window (e.g. 22:00 -> 06:00)
            return !localTime.isBefore(from) || !localTime.isAfter(to);
        }
    }
}

