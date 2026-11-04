package com.fsp.marketdata.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record TradingSession(
        ZoneId timezone,
        LocalTime openTime,
        LocalTime closeTime,
        Set<DayOfWeek> tradingDays,
        Set<LocalDate> holidays
) {

    public TradingSession {
        Objects.requireNonNull(timezone, "timezone must not be null");
        Objects.requireNonNull(openTime, "openTime must not be null");
        Objects.requireNonNull(closeTime, "closeTime must not be null");
        Objects.requireNonNull(tradingDays, "tradingDays must not be null");
        Objects.requireNonNull(holidays, "holidays must not be null");
        tradingDays = Set.copyOf(tradingDays);
        holidays = Set.copyOf(holidays);
    }

    public static TradingSession bistDefault(Set<LocalDate> holidays) {
        return new TradingSession(
                ZoneId.of("Europe/Istanbul"),
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                holidays
        );
    }

    public static TradingSession usEquityDefault(Set<LocalDate> holidays) {
        return new TradingSession(
                ZoneId.of("America/New_York"),
                LocalTime.of(9, 30),
                LocalTime.of(16, 0),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                holidays
        );
    }

    public static TradingSession forex24x5() {
        return new TradingSession(
                ZoneId.of("UTC"),
                LocalTime.MIN,
                LocalTime.MAX,
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                Set.of()
        );
    }

    public boolean isOpenAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        ZonedDateTime zdt = instant.atZone(timezone);
        LocalDate date = zdt.toLocalDate();
        if (holidays.contains(date) || !tradingDays.contains(zdt.getDayOfWeek())) {
            return false;
        }
        LocalTime time = zdt.toLocalTime();
        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }

    public boolean isNewSessionDay(Instant previousBarTime, Instant currentBarTime) {
        LocalDate prevDate = previousBarTime.atZone(timezone).toLocalDate();
        LocalDate currDate = currentBarTime.atZone(timezone).toLocalDate();
        return !prevDate.equals(currDate);
    }
}

