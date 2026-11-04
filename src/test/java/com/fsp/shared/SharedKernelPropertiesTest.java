package com.fsp.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

@Tag("SPEC-001")
@Tag("M0")
@org.junit.jupiter.api.Tag("SPEC-001")
@org.junit.jupiter.api.Tag("M0")
class SharedKernelPropertiesTest {

    @Property
    void priceAdditionSameCurrencyIsCommutative(
            @ForAll @DoubleRange(min = 0.01, max = 100_000.0) double a,
            @ForAll @DoubleRange(min = 0.01, max = 100_000.0) double b,
            @ForAll Currency currency
    ) {
        Price p1 = Price.of(a, currency);
        Price p2 = Price.of(b, currency);
        assertThat(p1.add(p2)).isEqualByComparingTo(p2.add(p1));
        assertThat(p1.add(p2).doubleValue()).isGreaterThan(p1.doubleValue());
    }

    @Property
    void priceOperationsAcrossDifferentCurrenciesThrowException(
            @ForAll @DoubleRange(min = 1.0, max = 10_000.0) double a,
            @ForAll @DoubleRange(min = 1.0, max = 10_000.0) double b
    ) {
        Price usd = Price.of(a, Currency.USD);
        Price tryPrice = Price.of(b, Currency.TRY);
        assertThatThrownBy(() -> usd.add(tryPrice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
        assertThatThrownBy(() -> usd.compareTo(tryPrice))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void percentageAndConfidenceEnforceInvariants(
            @ForAll @IntRange(min = 0, max = 100) int validConfidence,
            @ForAll @DoubleRange(min = -100.0, max = 100.0) double validScore
    ) {
        Confidence conf = new Confidence(validConfidence);
        assertThat(conf.value()).isBetween(0, 100);

        ConfluenceScore score = new ConfluenceScore(validScore);
        assertThat(score.abs()).isBetween(0.0, 100.0);
        assertThat(Confidence.fromConfluenceScore(score).value()).isBetween(0, 100);
    }

    @Test
    void timeframeCalculatesTimezoneAwareBarBoundaries() {
        ZoneId istanbul = ZoneId.of("Europe/Istanbul");
        Instant sample = ZonedDateTime.of(2026, 11, 4, 10, 17, 42, 0, istanbul).toInstant();

        Instant m15Start = Timeframe.M15.barStart(sample, istanbul);
        Instant m15End = Timeframe.M15.barEnd(m15Start, istanbul);

        assertThat(m15Start).isEqualTo(ZonedDateTime.of(2026, 11, 4, 10, 15, 0, 0, istanbul).toInstant());
        assertThat(m15End).isEqualTo(ZonedDateTime.of(2026, 11, 4, 10, 30, 0, 0, istanbul).toInstant());
        assertThat(Timeframe.M15.isBarClosed(m15Start, sample, istanbul)).isFalse();
        assertThat(Timeframe.M15.isBarClosed(m15Start, m15End, istanbul)).isTrue();
    }

    @Test
    void symbolNormalizesAndValidatesFormat() {
        assertThat(Symbol.of("  thyao ").value()).isEqualTo("THYAO");
        assertThat(Symbol.of("eurusd").value()).isEqualTo("EURUSD");
        assertThatThrownBy(() -> Symbol.of("invalid symbol!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void riskRewardComputesAccurateRatio() {
        Price entry = Price.of("100.00", Currency.TRY);
        Price sl = Price.of("95.00", Currency.TRY);
        Price tp = Price.of("112.50", Currency.TRY);
        RiskReward rr = RiskReward.of(entry, sl, tp);
        assertThat(rr.ratio()).isEqualTo(2.5);
    }
}
