package com.fsp.signal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsp.shared.ConfluenceScore;
import com.fsp.shared.Currency;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Price;
import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.shared.Vote;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Stateful property test verifying {@link SignalBook} against {@code docs/tla/SignalLifecycle.tla}:
 * <ul>
 *   <li>INV-SIG-1: AtMostOneActivePerKey</li>
 *   <li>INV-SIG-2: NoFlipFlop (consecutive signals on same key separated by >= Cooldown bars)</li>
 *   <li>INV-SIG-3: TerminalAbsorbing</li>
 *   <li>INV-SIG-4: No same-bar resolution (openedAt < bar on TP_HIT/SL_HIT)</li>
 *   <li>AppendOnly: Log never shrinks and key/dir/openedAt never mutate</li>
 * </ul>
 */
@Tag("FR-05")
@Tag("FR-06")
@Tag("INV-SIG-1")
@Tag("INV-SIG-2")
@Tag("INV-SIG-3")
@Tag("INV-SIG-4")
class SignalBookProperties {

    private static final Symbol[] SYMBOLS = {Symbol.of("EURUSD"), Symbol.of("THYAO")};
    private static final Timeframe[] TIMEFRAMES = {Timeframe.M5, Timeframe.H1};
    private static final Strategy STRATEGY = Strategy.defaultMultiAssetConfluence();
    private static final List<SignalWindow> ALWAYS_OPEN = List.of(SignalWindow.alwaysOpen());

    private record LogEntrySnapshot(
            Symbol symbol,
            Timeframe timeframe,
            Direction direction,
            long openedAtBar,
            SignalState state
    ) {}

    @Property
    void signalBookMaintainsAllTlaInvariantsAcrossArbitraryActionSequences(
            @ForAll @IntRange(min = 1, max = 4) int cooldown,
            @ForAll @IntRange(min = 2, max = 8) int maxHold,
            @ForAll @Size(min = 10, max = 80) List<@IntRange(min = 0, max = 4) Integer> actionCodes,
            @ForAll @Size(min = 10, max = 80) List<@IntRange(min = 0, max = 15) Integer> actionArgs
    ) {
        SignalBook book = new SignalBook(cooldown, maxHold);
        List<LogEntrySnapshot> previousSnapshot = List.of();
        Instant clock = Instant.parse("2026-11-04T12:00:00Z");

        int steps = Math.min(actionCodes.size(), actionArgs.size());
        for (int step = 0; step < steps; step++) {
            int code = actionCodes.get(step);
            int arg = actionArgs.get(step);
            clock = clock.plusSeconds(60);

            switch (code) {
                case 0 -> book.closeBar(); // CloseBar
                case 1 -> {
                    // Open(k, d) or Reverse(i)
                    Symbol sym = SYMBOLS[arg % SYMBOLS.length];
                    Timeframe tf = TIMEFRAMES[(arg / 2) % TIMEFRAMES.length];
                    Direction dir = (arg % 4 < 2) ? Direction.BUY : Direction.SELL;
                    openSample(book, sym, tf, dir, clock);
                }
                case 2 -> {
                    // Resolve(i, TP_HIT)
                    if (!book.signals().isEmpty()) {
                        int idx = arg % book.signals().size();
                        Signal target = book.signals().get(idx);
                        long openedBar = target.openedAtBar();
                        boolean resolved = book.resolve(idx, SignalState.TP_HIT, clock);
                        if (resolved) {
                            // INV-SIG-4: Must only resolve strictly after opening bar
                            assertThat(openedBar).isLessThan(book.currentBar());
                        }
                    }
                }
                case 3 -> {
                    // Resolve(i, SL_HIT)
                    if (!book.signals().isEmpty()) {
                        int idx = arg % book.signals().size();
                        Signal target = book.signals().get(idx);
                        long openedBar = target.openedAtBar();
                        boolean resolved = book.resolve(idx, SignalState.SL_HIT, clock);
                        if (resolved) {
                            // INV-SIG-4: Must only resolve strictly after opening bar
                            assertThat(openedBar).isLessThan(book.currentBar());
                        }
                    }
                }
                case 4 -> {
                    // Expire(i)
                    if (!book.signals().isEmpty()) {
                        int idx = arg % book.signals().size();
                        book.expire(idx, clock);
                    }
                }
            }

            List<Signal> current = book.signals();

            // 1. AppendOnly & INV-SIG-3 (TerminalAbsorbing)
            assertThat(current.size()).isGreaterThanOrEqualTo(previousSnapshot.size());
            for (int i = 0; i < previousSnapshot.size(); i++) {
                LogEntrySnapshot prev = previousSnapshot.get(i);
                Signal now = current.get(i);
                assertThat(now.symbol()).isEqualTo(prev.symbol());
                assertThat(now.timeframe()).isEqualTo(prev.timeframe());
                assertThat(now.direction()).isEqualTo(prev.direction());
                assertThat(now.openedAtBar()).isEqualTo(prev.openedAtBar());
                if (prev.state().isTerminal()) {
                    // INV-SIG-3: Terminal state never changes
                    assertThat(now.state()).isEqualTo(prev.state());
                }
            }

            // 2. INV-SIG-1 (AtMostOneActivePerKey) & INV-SIG-2 (NoFlipFlop)
            for (int i = 0; i < current.size(); i++) {
                for (int j = i + 1; j < current.size(); j++) {
                    Signal si = current.get(i);
                    Signal sj = current.get(j);
                    if (si.symbol().equals(sj.symbol()) && si.timeframe() == sj.timeframe()) {
                        // INV-SIG-1
                        assertThat(si.state() == SignalState.ACTIVE && sj.state() == SignalState.ACTIVE)
                                .isFalse();
                        // INV-SIG-2
                        assertThat(sj.openedAtBar() - si.openedAtBar())
                                .isGreaterThanOrEqualTo(cooldown);
                    }
                }
            }

            previousSnapshot = snapshot(current);
        }
    }

    private static void openSample(SignalBook book, Symbol sym, Timeframe tf, Direction dir, Instant now) {
        Price entry = Price.of(100.0, Currency.USD);
        Price sl = dir == Direction.BUY ? Price.of(98.0, Currency.USD) : Price.of(102.0, Currency.USD);
        List<Price> tps = dir == Direction.BUY
                ? List.of(Price.of(103.0, Currency.USD), Price.of(105.0, Currency.USD), Price.of(108.0, Currency.USD))
                : List.of(Price.of(97.0, Currency.USD), Price.of(95.0, Currency.USD), Price.of(92.0, Currency.USD));
        ConfluenceScore score = new ConfluenceScore(dir == Direction.BUY ? 72.0 : -72.0);
        List<Vote> rationale = List.of(new Vote("EMA_STACK", "TREND", dir, dir.sign() * 0.9, 1.5, "Trend vote"));
        book.tryOpenOrReverse(sym, tf, dir, entry, sl, tps, score, MarketRegime.TRENDING_UP, rationale, STRATEGY, now, ALWAYS_OPEN);
    }

    private static List<LogEntrySnapshot> snapshot(List<Signal> signals) {
        List<LogEntrySnapshot> list = new ArrayList<>(signals.size());
        for (Signal s : signals) {
            list.add(new LogEntrySnapshot(s.symbol(), s.timeframe(), s.direction(), s.openedAtBar(), s.state()));
        }
        return list;
    }
}

