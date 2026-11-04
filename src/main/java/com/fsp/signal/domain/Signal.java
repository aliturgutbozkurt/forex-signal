package com.fsp.signal.domain;

import com.fsp.shared.Confidence;
import com.fsp.shared.ConfluenceScore;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Price;
import com.fsp.shared.RiskReward;
import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.shared.Vote;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jspecify.annotations.Nullable;

/**
 * Signal Aggregate Root enforcing price ordering, Risk:Reward, SignalWindow, and state machine invariants
 * (SPEC-001 §6.1, §6.3, FR-03, FR-05, FR-06, INV-SIG-1..4).
 */
@AggregateRoot
public final class Signal {

    private final SignalId id;
    private final Symbol symbol;
    private final Timeframe timeframe;
    private final Direction direction;
    private final Price entry;
    private final Price stopLoss;
    private final List<Price> takeProfits;
    private final RiskReward riskRewardTp1;
    private final Confidence confidence;
    private final ConfluenceScore confluenceScore;
    private final MarketRegime regime;
    private final List<Vote> rationale;
    private final String strategyId;
    private final int strategyVersion;
    private final long openedAtBar;
    private final Instant openedAt;

    private SignalState state;
    private @Nullable Instant closedAt;
    private @Nullable String explanation;

    private Signal(
            SignalId id,
            Symbol symbol,
            Timeframe timeframe,
            Direction direction,
            Price entry,
            Price stopLoss,
            List<Price> takeProfits,
            RiskReward riskRewardTp1,
            Confidence confidence,
            ConfluenceScore confluenceScore,
            MarketRegime regime,
            List<Vote> rationale,
            String strategyId,
            int strategyVersion,
            long openedAtBar,
            Instant openedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.symbol = Objects.requireNonNull(symbol);
        this.timeframe = Objects.requireNonNull(timeframe);
        this.direction = Objects.requireNonNull(direction);
        this.entry = Objects.requireNonNull(entry);
        this.stopLoss = Objects.requireNonNull(stopLoss);
        this.takeProfits = List.copyOf(Objects.requireNonNull(takeProfits));
        this.riskRewardTp1 = Objects.requireNonNull(riskRewardTp1);
        this.confidence = Objects.requireNonNull(confidence);
        this.confluenceScore = Objects.requireNonNull(confluenceScore);
        this.regime = Objects.requireNonNull(regime);
        this.rationale = List.copyOf(Objects.requireNonNull(rationale));
        this.strategyId = Objects.requireNonNull(strategyId);
        this.strategyVersion = strategyVersion;
        if (openedAtBar < 0) {
            throw new IllegalArgumentException("openedAtBar must be >= 0");
        }
        this.openedAtBar = openedAtBar;
        this.openedAt = Objects.requireNonNull(openedAt);
        this.state = SignalState.ACTIVE;
    }

    public static Signal open(
            SignalId id,
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
            long openedAtBar,
            Instant openedAt,
            List<SignalWindow> allowedWindows
    ) {
        Objects.requireNonNull(allowedWindows, "allowedWindows must not be null");
        if (!allowedWindows.isEmpty() && allowedWindows.stream().noneMatch(w -> w.isAllowedAt(openedAt))) {
            throw new SignalWindowClosedException(
                    "Signal generation rejected outside active SignalWindow for " + symbol + " at " + openedAt);
        }
        if (takeProfits.size() < 3) {
            throw new IllegalArgumentException("Signal requires 3 take-profit levels (TP1, TP2, TP3)");
        }
        Price tp1 = takeProfits.get(0);
        Price tp2 = takeProfits.get(1);
        Price tp3 = takeProfits.get(2);

        if (direction == Direction.BUY) {
            // Invariant: BUY requires SL < entry < TP1 < TP2 < TP3
            if (!(stopLoss.compareTo(entry) < 0
                    && entry.compareTo(tp1) < 0
                    && tp1.compareTo(tp2) < 0
                    && tp2.compareTo(tp3) < 0)) {
                throw new IllegalArgumentException(
                        "BUY signal invariant violated: require SL < entry < TP1 < TP2 < TP3");
            }
        } else {
            // Invariant: SELL requires SL > entry > TP1 > TP2 > TP3
            if (!(stopLoss.compareTo(entry) > 0
                    && entry.compareTo(tp1) > 0
                    && tp1.compareTo(tp2) > 0
                    && tp2.compareTo(tp3) > 0)) {
                throw new IllegalArgumentException(
                        "SELL signal invariant violated: require SL > entry > TP1 > TP2 > TP3");
            }
        }

        RiskReward rr1 = RiskReward.of(entry, stopLoss, tp1);
        if (rr1.ratio() + 1e-6 < strategy.riskPolicy().minRiskReward()) {
            throw new IllegalArgumentException(
                    "Risk:Reward(TP1) " + rr1.ratio() + " below minimum " + strategy.riskPolicy().minRiskReward());
        }

        return new Signal(
                id,
                symbol,
                timeframe,
                direction,
                entry,
                stopLoss,
                takeProfits,
                rr1,
                Confidence.fromConfluenceScore(confluenceScore),
                confluenceScore,
                regime,
                rationale,
                strategy.strategyId(),
                strategy.version(),
                openedAtBar,
                openedAt
        );
    }

    /**
     * Evaluates bar closure against TP1, SL, or maxHold expiration.
     * Enforces INV-SIG-3 (TerminalAbsorbing) and INV-SIG-4 (cannot resolve on opening bar).
     */
    public SignalState onBarClosed(long barIndex, double barHigh, double barLow, int maxHoldBars, Instant barCloseTime) {
        if (state.isTerminal()) {
            // INV-SIG-3: Terminal states are absorbing
            return state;
        }
        if (barIndex <= openedAtBar) {
            // INV-SIG-4: A signal cannot resolve on the bar it was opened
            return state;
        }

        double sl = stopLoss.doubleValue();
        double tp1 = takeProfits.getFirst().doubleValue();

        if (direction == Direction.BUY) {
            if (barLow <= sl) {
                transitionToTerminal(SignalState.SL_HIT, barCloseTime);
                return state;
            }
            if (barHigh >= tp1) {
                transitionToTerminal(SignalState.TP_HIT, barCloseTime);
                return state;
            }
        } else {
            if (barHigh >= sl) {
                transitionToTerminal(SignalState.SL_HIT, barCloseTime);
                return state;
            }
            if (barLow <= tp1) {
                transitionToTerminal(SignalState.TP_HIT, barCloseTime);
                return state;
            }
        }

        if (barIndex - openedAtBar >= maxHoldBars) {
            transitionToTerminal(SignalState.EXPIRED, barCloseTime);
        }
        return state;
    }

    public void resolveDirectly(SignalState outcome, long currentBarIndex, Instant now) {
        if (outcome != SignalState.TP_HIT && outcome != SignalState.SL_HIT) {
            throw new IllegalArgumentException("Direct resolution must be TP_HIT or SL_HIT");
        }
        if (state.isTerminal()) {
            return;
        }
        if (currentBarIndex <= openedAtBar) {
            throw new IllegalStateException("INV-SIG-4: Cannot resolve signal on its opening bar");
        }
        transitionToTerminal(outcome, now);
    }

    public void expireIfEligible(long currentBarIndex, int maxHoldBars, Instant now) {
        if (state.isTerminal()) {
            return;
        }
        if (currentBarIndex - openedAtBar < maxHoldBars) {
            throw new IllegalStateException("Signal has not reached maxHoldBars yet");
        }
        transitionToTerminal(SignalState.EXPIRED, now);
    }

    public void invalidateByOpposite(Instant now) {
        if (state.isTerminal()) {
            // INV-SIG-3: Terminal states are absorbing
            return;
        }
        transitionToTerminal(SignalState.INVALIDATED, now);
    }

    public void attachExplanation(String explanationText) {
        Objects.requireNonNull(explanationText, "explanationText must not be null");
        this.explanation = explanationText;
    }

    private void transitionToTerminal(SignalState target, Instant timestamp) {
        if (this.state.isTerminal()) {
            return;
        }
        if (!target.isTerminal()) {
            throw new IllegalArgumentException("Target must be a terminal state: " + target);
        }
        this.state = target;
        this.closedAt = timestamp;
    }

    public SignalId id() {
        return id;
    }

    public Symbol symbol() {
        return symbol;
    }

    public Timeframe timeframe() {
        return timeframe;
    }

    public Direction direction() {
        return direction;
    }

    public Price entry() {
        return entry;
    }

    public Price stopLoss() {
        return stopLoss;
    }

    public List<Price> takeProfits() {
        return takeProfits;
    }

    public Price tp1() {
        return takeProfits.get(0);
    }

    public Price tp2() {
        return takeProfits.get(1);
    }

    public Price tp3() {
        return takeProfits.get(2);
    }

    public RiskReward riskRewardTp1() {
        return riskRewardTp1;
    }

    public Confidence confidence() {
        return confidence;
    }

    public ConfluenceScore confluenceScore() {
        return confluenceScore;
    }

    public MarketRegime regime() {
        return regime;
    }

    public List<Vote> rationale() {
        return rationale;
    }

    public String strategyId() {
        return strategyId;
    }

    public int strategyVersion() {
        return strategyVersion;
    }

    public long openedAtBar() {
        return openedAtBar;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public SignalState state() {
        return state;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<String> explanation() {
        return Optional.ofNullable(explanation);
    }
}

