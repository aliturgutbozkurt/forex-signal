package com.fsp.shared;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public enum Timeframe {
    M1("1m", "1", Duration.ofMinutes(1)),
    M3("3m", "3", Duration.ofMinutes(3)),
    M5("5m", "5", Duration.ofMinutes(5)),
    M15("15m", "15", Duration.ofMinutes(15)),
    M30("30m", "30", Duration.ofMinutes(30)),
    H1("1h", "60", Duration.ofHours(1)),
    H2("2h", "120", Duration.ofHours(2)),
    H4("4h", "240", Duration.ofHours(4)),
    H8("8h", "480", Duration.ofHours(8)),
    D1("1D", "1D", Duration.ofDays(1)),
    W1("1W", "1W", Duration.ofDays(7)),
    MN("1M", "1M", Duration.ofDays(30));

    private final String label;
    private final String scannerSuffix;
    private final Duration duration;

    Timeframe(String label, String scannerSuffix, Duration duration) {
        this.label = label;
        this.scannerSuffix = scannerSuffix;
        this.duration = duration;
    }

    public String label() {
        return label;
    }

    public String labelTr() {
        return switch (this) {
            case M1 -> "1dk";
            case M3 -> "3dk";
            case M5 -> "5dk";
            case M15 -> "15dk";
            case M30 -> "30dk";
            case H1 -> "1s";
            case H2 -> "2s";
            case H4 -> "4s";
            case H8 -> "8s";
            case D1 -> "1G";
            case W1 -> "1H";
            case MN -> "1A";
        };
    }

    public String tradingViewInterval() {
        return switch (this) {
            case M1 -> "1";
            case M3 -> "3";
            case M5 -> "5";
            case M15 -> "15";
            case M30 -> "30";
            case H1 -> "60";
            case H2 -> "120";
            case H4 -> "240";
            case H8 -> "480";
            case D1 -> "D";
            case W1 -> "W";
            case MN -> "M";
        };
    }

    public String scannerSuffix() {
        return scannerSuffix;
    }

    public Duration duration() {
        return duration;
    }

    public String scannerColumn(String baseIndicator) {
        Objects.requireNonNull(baseIndicator, "baseIndicator must not be null");
        if (this == D1) {
            return baseIndicator;
        }
        return baseIndicator + "|" + scannerSuffix;
    }

    /**
     * Computes the exact opening timestamp of the bar containing {@code instant} in {@code zone}.
     */
    public Instant barStart(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        ZonedDateTime zdt = instant.atZone(zone);
        return switch (this) {
            case M1, M3, M5, M15, M30 -> {
                int stepMinutes = (int) duration.toMinutes();
                int flooredMinute = (zdt.getMinute() / stepMinutes) * stepMinutes;
                yield zdt.withMinute(flooredMinute).truncatedTo(ChronoUnit.MINUTES).toInstant();
            }
            case H1, H2, H4, H8 -> {
                int stepHours = (int) duration.toHours();
                int flooredHour = (zdt.getHour() / stepHours) * stepHours;
                yield zdt.withHour(flooredHour).truncatedTo(ChronoUnit.HOURS).toInstant();
            }
            case D1 -> zdt.truncatedTo(ChronoUnit.DAYS).toInstant();
            case W1 -> zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .truncatedTo(ChronoUnit.DAYS)
                    .toInstant();
            case MN -> zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }

    /**
     * Computes the closing boundary (exclusive end) of the bar starting at {@code barStartInstant} in {@code zone}.
     */
    public Instant barEnd(Instant barStartInstant, ZoneId zone) {
        Objects.requireNonNull(barStartInstant, "barStartInstant must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        ZonedDateTime start = barStart(barStartInstant, zone).atZone(zone);
        return switch (this) {
            case M1, M3, M5, M15, M30 -> start.plusMinutes(duration.toMinutes()).toInstant();
            case H1, H2, H4, H8 -> start.plusHours(duration.toHours()).toInstant();
            case D1 -> start.plusDays(1).toInstant();
            case W1 -> start.plusWeeks(1).toInstant();
            case MN -> start.plusMonths(1).toInstant();
        };
    }

    /**
     * Returns true iff a bar that opened at {@code barOpen} has completely closed at {@code now} (INV-BAR-1 no-repaint).
     */
    public boolean isBarClosed(Instant barOpen, Instant now, ZoneId zone) {
        Instant closeBoundary = barEnd(barOpen, zone);
        return !now.isBefore(closeBoundary);
    }

    public Timeframe higherTimeframe() {
        return switch (this) {
            case M1 -> M5;
            case M3 -> M15;
            case M5 -> M15;
            case M15 -> H1;
            case M30 -> H2;
            case H1 -> H4;
            case H2 -> H8;
            case H4 -> D1;
            case H8 -> D1;
            case D1 -> W1;
            case W1, MN -> MN;
        };
    }
}

