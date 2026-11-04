package com.fsp.signal.domain;

import com.fsp.shared.ConfluenceScore;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Price;
import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.shared.Vote;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.ddd.annotation.AggregateRoot;

/**
 * Consistency boundary for signals modeled directly on {@code docs/tla/SignalLifecycle.tla}.
 * Guarantees:
 * <ul>
 *   <li><b>INV-SIG-1 (AtMostOneActivePerKey):</b> At most one ACTIVE signal per (Symbol, Timeframe).</li>
 *   <li><b>INV-SIG-2 (NoFlipFlop):</b> Consecutive signals on the same key are separated by at least {@code cooldownBars}.</li>
 *   <li><b>INV-SIG-3 (TerminalAbsorbing):</b> Terminal states never change.</li>
 *   <li><b>INV-SIG-4:</b> A signal never resolves on its opening bar.</li>
 *   <li><b>AppendOnly:</b> Signal log never shrinks or mutates key/dir/openedAtBar.</li>
 * </ul>
 */
@AggregateRoot
public final class SignalBook {

    private final int cooldownBars;
    private final int maxHoldBars;
    private long currentBar = 0L;
    private final List<Signal> signalLog = new ArrayList<>();

    public SignalBook(int cooldownBars, int maxHoldBars) {
        if (cooldownBars < 1) {
            throw new IllegalArgumentException("cooldownBars must be >= 1");
        }
        if (maxHoldBars < 1) {
            throw new IllegalArgumentException("maxHoldBars must be >= 1");
        }
        this.cooldownBars = cooldownBars;
        this.maxHoldBars = maxHoldBars;
    }

    public long currentBar() {
        return currentBar;
    }

    public int cooldownBars() {
        return cooldownBars;
    }

    public int maxHoldBars() {
        return maxHoldBars;
    }

    public List<Signal> signals() {
        return Collections.unmodifiableList(signalLog);
    }

    /**
     * TLA+ {@code CloseBar}: advances the global closed bar index.
     */
    public void closeBar() {
        this.currentBar++;
    }

    public void advanceBarTo(long barIndex) {
        if (barIndex > this.currentBar) {
            this.currentBar = barIndex;
        }
    }

    public boolean hasActive(Symbol symbol, Timeframe timeframe) {
        return findActive(symbol, timeframe).isPresent();
    }

    public Optional<Signal> findActive(Symbol symbol, Timeframe timeframe) {
        for (Signal s : signalLog) {
            if (s.symbol().equals(symbol) && s.timeframe() == timeframe && s.state() == SignalState.ACTIVE) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public boolean isCooldownSatisfied(Symbol symbol, Timeframe timeframe) {
        for (Signal s : signalLog) {
            if (s.symbol().equals(symbol) && s.timeframe() == timeframe) {
                if (currentBar - s.openedAtBar() < cooldownBars) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * TLA+ {@code Open(k, d)} or {@code Reverse(i)}:
     * - If no active signal exists on key and cooldown is satisfied -> opens new ACTIVE signal.
     * - If an active signal in the OPPOSITE direction exists and cooldown is satisfied ->
     *   invalidates the active signal and appends the new opposite ACTIVE signal.
     */
    public Optional<Signal> tryOpenOrReverse(
            Symbol symbol,
            Timeframe timeframe,
            Direction direction,
            Price entry,
            Price stopLoss,
            List<Price> takeProfits,
            ConfluenceScore confluenceScore,
            MarketRegime regime,
            List<Vote> rationale,
            Strategy strategy,
            Instant now,
            List<SignalWindow> windows
    ) {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(timeframe);
        Objects.requireNonNull(direction);

        if (!isCooldownSatisfied(symbol, timeframe)) {
            // INV-SIG-2: Cooldown not yet elapsed
            return Optional.empty();
        }

        Optional<Signal> existingActive = findActive(symbol, timeframe);
        if (existingActive.isPresent()) {
            Signal active = existingActive.get();
            if (active.direction() == direction) {
                // INV-SIG-1: Same direction already active
                return Optional.empty();
            }
            // Validate new signal before invalidating old one so window/RR failure leaves state untouched
            Signal replacement = Signal.open(
                    SignalId.random(),
                    symbol,
                    timeframe,
                    direction,
                    entry,
                    stopLoss,
                    takeProfits,
                    confluenceScore,
                    regime,
                    rationale,
                    strategy,
                    currentBar,
                    now,
                    windows
            );
            active.invalidateByOpposite(now);
            signalLog.add(replacement);
            return Optional.of(replacement);
        }

        Signal opened = Signal.open(
                SignalId.random(),
                symbol,
                timeframe,
                direction,
                entry,
                stopLoss,
                takeProfits,
                confluenceScore,
                regime,
                rationale,
                strategy,
                currentBar,
                now,
                windows
        );
        signalLog.add(opened);
        return Optional.of(opened);
    }

    /**
     * TLA+ {@code Resolve(i, outcome)} guard & action.
     */
    public boolean resolve(int logIndex, SignalState outcome, Instant now) {
        if (logIndex < 0 || logIndex >= signalLog.size()) {
            return false;
        }
        if (outcome != SignalState.TP_HIT && outcome != SignalState.SL_HIT) {
            return false;
        }
        Signal target = signalLog.get(logIndex);
        if (target.state() != SignalState.ACTIVE || target.openedAtBar() >= currentBar) {
            return false;
        }
        target.resolveDirectly(outcome, currentBar, now);
        return true;
    }

    /**
     * TLA+ {@code Expire(i)} guard & action.
     */
    public boolean expire(int logIndex, Instant now) {
        if (logIndex < 0 || logIndex >= signalLog.size()) {
            return false;
        }
        Signal target = signalLog.get(logIndex);
        if (target.state() != SignalState.ACTIVE || (currentBar - target.openedAtBar()) < maxHoldBars) {
            return false;
        }
        target.expireIfEligible(currentBar, maxHoldBars, now);
        return true;
    }
}

