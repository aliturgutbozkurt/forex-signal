package com.fsp.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fsp.analysis.api.DivergenceType;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.analysis.api.PivotLevels;
import com.fsp.marketdata.api.ScannerIndicatorValues;
import com.fsp.shared.ConfluenceScore;
import com.fsp.shared.Currency;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Price;
import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.shared.Vote;
import com.fsp.signal.domain.ConfluenceScorer;
import com.fsp.signal.domain.RiskPolicy;
import com.fsp.signal.domain.Signal;
import com.fsp.signal.domain.SignalId;
import com.fsp.signal.domain.SignalWindowClosedException;
import com.fsp.signal.domain.Strategy;
import com.fsp.watchlist.api.WatchlistChanged;
import com.fsp.watchlist.application.WatchlistApplicationService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("FR-02")
@Tag("FR-03")
@Tag("FR-04")
@Tag("FR-05")
@Tag("FR-16")
@Tag("M3")
class SignalGenerationTests {

    @Test
    @Tag("FR-03")
    void signalWindowRejectsOpenOutsideAllowedHoursAndHandlesDstTransitions() {
        SignalWindow bistWindow = SignalWindow.bistSession(); // 10:00–18:00 Europe/Istanbul
        Instant insideBist = ZonedDateTime.of(2026, 11, 4, 14, 30, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant();
        Instant outsideBist = ZonedDateTime.of(2026, 11, 4, 21, 15, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant();

        Strategy strategy = Strategy.defaultMultiAssetConfluence();
        Price entry = Price.of("312.50", Currency.TRY);
        Price sl = Price.of("305.00", Currency.TRY);
        List<Price> tps = List.of(
                Price.of("323.75", Currency.TRY),
                Price.of("331.25", Currency.TRY),
                Price.of("342.50", Currency.TRY)
        );
        List<Vote> votes = List.of(new Vote("EMA_STACK", "TREND", Direction.BUY, 0.9, 1.5, "EMA pozitif"));

        Signal valid = Signal.open(
                SignalId.random(), Symbol.of("THYAO"), Timeframe.H1, Direction.BUY,
                entry, sl, tps, new ConfluenceScore(68.0), MarketRegime.TRENDING_UP,
                votes, strategy, 10L, insideBist, List.of(bistWindow)
        );
        assertThat(valid.symbol()).isEqualTo(Symbol.of("THYAO"));

        assertThatThrownBy(() -> Signal.open(
                SignalId.random(), Symbol.of("THYAO"), Timeframe.H1, Direction.BUY,
                entry, sl, tps, new ConfluenceScore(68.0), MarketRegime.TRENDING_UP,
                votes, strategy, 11L, outsideBist, List.of(bistWindow)
        )).isInstanceOf(SignalWindowClosedException.class);

        // DST transition test on America/New_York (spring forward & fall back)
        SignalWindow usWindow = SignalWindow.usRegularSession(); // 09:30–16:00 NY
        Instant nySummerOpen = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        Instant nyWinterOpen = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        assertThat(usWindow.isAllowedAt(nySummerOpen)).isTrue();
        assertThat(usWindow.isAllowedAt(nyWinterOpen)).isTrue();
    }

    @Test
    @Tag("FR-04")
    void confluenceScorerProducesDeterministicScoreAndPenalizesMtfDisagreement() {
        ConfluenceScorer scorer = new ConfluenceScorer();
        Strategy strategy = Strategy.defaultMultiAssetConfluence();
        IndicatorSnapshotDto bullishSnap = sampleSnapshot(Symbol.of("EURUSD"), Timeframe.M15, true, 1.5);
        ScannerIndicatorValues scanner = new ScannerIndicatorValues(0.65, 0.70, 0.55, 62.0, 28.0, true);

        ConfluenceScorer.ScoringResult r1 = scorer.score(bullishSnap, scanner, null, strategy);
        ConfluenceScorer.ScoringResult r2 = scorer.score(bullishSnap, scanner, null, strategy);

        // Golden determinism check
        assertThat(r1.score().value()).isEqualTo(r2.score().value());
        assertThat(r1.score().value()).isGreaterThan(50.0);
        assertThat(r1.volumeConfirmed()).isTrue();

        // Higher timeframe (H1) bearish -> MTF disagreement penalizes score
        IndicatorSnapshotDto bearishH1 = sampleSnapshot(Symbol.of("EURUSD"), Timeframe.H1, false, 1.5);
        ConfluenceScorer.ScoringResult mtfDisagreed = scorer.score(bullishSnap, scanner, bearishH1, strategy);
        assertThat(mtfDisagreed.mtfAligned()).isFalse();
        assertThat(mtfDisagreed.score().value()).isLessThan(r1.score().value() * 0.65);
    }

    @Test
    @Tag("FR-05")
    @Tag("FR-16")
    void strategyVersioningPreservesHistoricalSignalVersionAndEnforcesPriceAndRrInvariants() {
        Strategy v1 = Strategy.defaultMultiAssetConfluence();
        Strategy v2 = v1.withUpdatedConfiguration(v1.allRegimeWeights(), new RiskPolicy(1.8, 1.6, 2.8, 4.5, 1.6, 4, 30, 1.2));

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);

        List<Vote> votes = List.of(new Vote("MACD", "MOMENTUM", Direction.BUY, 0.85, 1.0, "MACD pozitif"));
        Signal sigV1 = Signal.open(
                SignalId.random(), Symbol.of("EURUSD"), Timeframe.H1, Direction.BUY,
                Price.of("1.0800", Currency.USD), Price.of("1.0750", Currency.USD),
                List.of(Price.of("1.0880", Currency.USD), Price.of("1.0930", Currency.USD), Price.of("1.1000", Currency.USD)),
                new ConfluenceScore(65.0), MarketRegime.TRENDING_UP, votes, v1, 5L, Instant.now(), List.of(SignalWindow.alwaysOpen())
        );
        assertThat(sigV1.strategyVersion()).isEqualTo(1);

        // Violate R:R invariant (TP1 too close to entry)
        assertThatThrownBy(() -> Signal.open(
                SignalId.random(), Symbol.of("EURUSD"), Timeframe.H1, Direction.BUY,
                Price.of("1.0800", Currency.USD), Price.of("1.0700", Currency.USD),
                List.of(Price.of("1.0850", Currency.USD), Price.of("1.0950", Currency.USD), Price.of("1.1050", Currency.USD)),
                new ConfluenceScore(65.0), MarketRegime.TRENDING_UP, votes, v1, 6L, Instant.now(), List.of(SignalWindow.alwaysOpen())
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("below minimum");
    }

    @Test
    @Tag("FR-02")
    void watchlistCrudPublishesWatchlistChangedEvent() {
        List<Object> events = new ArrayList<>();
        WatchlistApplicationService wlService = new WatchlistApplicationService(events::add);
        wlService.addOrUpdateSubscription(Symbol.of("MSFT"), EnumSet.of(Timeframe.M15, Timeframe.H4));

        assertThat(events).anyMatch(WatchlistChanged.class::isInstance);
        assertThat(wlService.getSubscriptions())
                .anyMatch(s -> s.symbol().equals(Symbol.of("MSFT")) && s.timeframes().contains(Timeframe.H4));
    }

    private static IndicatorSnapshotDto sampleSnapshot(Symbol symbol, Timeframe tf, boolean bullish, double rvol) {
        PivotLevels pivots = new PivotLevels(100, 102, 104, 106, 98, 96, 94, 101, 102, 99, 98, 101, 102, 99, 98, 105, 95);
        if (bullish) {
            return new IndicatorSnapshotDto(
                    symbol, tf, 20, Instant.now(), 102.0,
                    101.5, 100.8, 99.5, 97.0, 100.5, 101.6,
                    101.2, 100.4, 100.8, 99.8,
                    99.9, true, 99.5, true, 31.0, 32.0, 14.0,
                    63.0, 72.0, 64.0, 75.0, 0.45, 0.30, 0.15, 110.0, -22.0, 2.5, 1.2, 64.0,
                    DivergenceType.NONE, DivergenceType.NONE,
                    0.8, 0.78, 103.5, 100.5, 97.5, 0.05, false, 103.0, 98.5, 104.0, 97.0,
                    rvol, 25000.0, 100.9, 101.8, 100.0, 0.18, 66.0, 18000.0, 100.6, 102.5, 99.2,
                    pivots, MarketRegime.TRENDING_UP
            );
        } else {
            return new IndicatorSnapshotDto(
                    symbol, tf, 20, Instant.now(), 98.0,
                    98.5, 99.2, 100.5, 103.0, 99.5, 98.2,
                    98.8, 99.6, 99.2, 100.2,
                    100.4, false, 100.5, false, 31.0, 14.0, 32.0,
                    37.0, 28.0, 36.0, 25.0, -0.45, -0.30, -0.15, -110.0, -78.0, -2.5, -1.2, 36.0,
                    DivergenceType.NONE, DivergenceType.NONE,
                    0.8, 0.81, 102.5, 99.5, 96.5, 0.05, false, 101.5, 97.0, 103.0, 96.0,
                    rvol, -25000.0, 99.2, 100.1, 98.3, -0.18, 34.0, -18000.0, 99.4, 101.0, 97.5,
                    pivots, MarketRegime.TRENDING_DOWN
            );
        }
    }
}

