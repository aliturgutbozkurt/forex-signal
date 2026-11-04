package com.fsp.signal.application;

import com.fsp.analysis.api.AnalysisFacade;
import com.fsp.analysis.api.IndicatorSnapshotComputed;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.marketdata.api.InstrumentDto;
import com.fsp.marketdata.api.MarketDataFacade;
import com.fsp.marketdata.api.ScannerIndicatorValues;
import com.fsp.shared.Currency;
import com.fsp.shared.Direction;
import com.fsp.shared.Price;
import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.signal.api.SignalClosed;
import com.fsp.signal.api.SignalDto;
import com.fsp.signal.api.SignalExpired;
import com.fsp.signal.api.SignalFacade;
import com.fsp.signal.api.SignalInvalidated;
import com.fsp.signal.api.SignalOpened;
import com.fsp.signal.api.StopLossHit;
import com.fsp.signal.api.StrategySummaryDto;
import com.fsp.signal.api.TakeProfitHit;
import com.fsp.signal.domain.ConfluenceScorer;
import com.fsp.signal.domain.RiskPolicy;
import com.fsp.signal.domain.Signal;
import com.fsp.signal.domain.SignalBook;
import com.fsp.signal.domain.SignalState;
import com.fsp.signal.domain.SignalWindowClosedException;
import com.fsp.signal.domain.Strategy;
import com.fsp.watchlist.api.WatchlistFacade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class SignalApplicationService implements SignalFacade {

    private static final Logger log = LoggerFactory.getLogger(SignalApplicationService.class);

    private final MarketDataFacade marketDataFacade;
    private final AnalysisFacade analysisFacade;
    private final WatchlistFacade watchlistFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final ConfluenceScorer scorer = new ConfluenceScorer();

    private volatile Strategy activeStrategy = Strategy.defaultMultiAssetConfluence();
    private final Map<String, SignalBook> booksByKey = new ConcurrentHashMap<>();
    private final Map<String, Signal> signalsById = new ConcurrentHashMap<>();

    public SignalApplicationService(
            MarketDataFacade marketDataFacade,
            AnalysisFacade analysisFacade,
            WatchlistFacade watchlistFacade,
            ApplicationEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate
    ) {
        this.marketDataFacade = marketDataFacade;
        this.analysisFacade = analysisFacade;
        this.watchlistFacade = watchlistFacade;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener
    public void onIndicatorSnapshotComputed(IndicatorSnapshotComputed event) {
        evaluateSnapshot(event.snapshot());
    }

    @Override
    public Optional<SignalDto> evaluateSnapshot(IndicatorSnapshotDto snap) {
        Strategy strategy = this.activeStrategy;
        String key = snap.symbol().value() + ":" + snap.timeframe().name();
        SignalBook book = booksByKey.computeIfAbsent(key, _ -> new SignalBook(
                strategy.riskPolicy().cooldownBars(),
                strategy.riskPolicy().maxHoldBars()
        ));

        synchronized (book) {
            book.advanceBarTo(snap.barIndex());

            // 1. Evaluate any existing ACTIVE signal for TP/SL/Expire on this closed bar
            Optional<Signal> activeOpt = book.findActive(snap.symbol(), snap.timeframe());
            if (activeOpt.isPresent()) {
                Signal active = activeOpt.get();
                double barHigh = Math.max(snap.close(), snap.pivots().swingHigh());
                double barLow = Math.min(snap.close(), snap.pivots().swingLow());
                SignalState nextState = active.onBarClosed(
                        snap.barIndex(),
                        barHigh,
                        barLow,
                        strategy.riskPolicy().maxHoldBars(),
                        snap.closeTime()
                );
                if (nextState.isTerminal()) {
                    publishTerminalOutcome(active, nextState);
                }
            }

            // 2. Score confluence
            ScannerIndicatorValues scanner = marketDataFacade.getScannerSnapshot(snap.symbol(), snap.timeframe());
            IndicatorSnapshotDto higherTf = analysisFacade
                    .getLatestSnapshot(snap.symbol(), snap.timeframe().higherTimeframe())
                    .orElse(null);

            ConfluenceScorer.ScoringResult scoring = scorer.score(snap, scanner, higherTf, strategy);
            double threshold = strategy.weightsFor(snap.regime()).entryThreshold();
            if (scoring.score().abs() < threshold || !scoring.volumeConfirmed()) {
                return Optional.empty();
            }

            Direction dir = scoring.score().direction().orElse(Direction.BUY);
            InstrumentDto inst = marketDataFacade.findInstrument(snap.symbol()).orElse(null);
            Currency currency = inst != null ? inst.quoteCurrency() : Currency.USD;
            BigDecimal tickSize = inst != null ? inst.tickSize() : new BigDecimal("0.0001");

            double entryVal = snap.close();
            double atr = Math.max(snap.atr14(), entryVal * 0.0025);
            double slDist = atr * strategy.riskPolicy().slAtrMultiplier();

            double rawSl = dir == Direction.BUY ? entryVal - slDist : entryVal + slDist;
            double snappedSl = snap.pivots().snapStopLoss(dir, entryVal, rawSl, atr);
            double risk = Math.max(tickSize.doubleValue() * 5.0, Math.abs(entryVal - snappedSl));
            snappedSl = dir == Direction.BUY ? entryVal - risk : entryVal + risk;

            double tp1Val = dir == Direction.BUY
                    ? entryVal + risk * strategy.riskPolicy().tp1RiskMultiple()
                    : entryVal - risk * strategy.riskPolicy().tp1RiskMultiple();
            double tp2Val = dir == Direction.BUY
                    ? entryVal + risk * strategy.riskPolicy().tp2RiskMultiple()
                    : entryVal - risk * strategy.riskPolicy().tp2RiskMultiple();
            double tp3Val = dir == Direction.BUY
                    ? entryVal + risk * strategy.riskPolicy().tp3RiskMultiple()
                    : entryVal - risk * strategy.riskPolicy().tp3RiskMultiple();

            if (snappedSl <= 0 || tp1Val <= 0 || tp2Val <= 0 || tp3Val <= 0) {
                return Optional.empty();
            }

            Price entry = Price.of(entryVal, currency).snapToTick(tickSize);
            Price sl = Price.of(snappedSl, currency).snapToTick(tickSize);
            Price tp1 = Price.of(tp1Val, currency).snapToTick(tickSize);
            Price tp2 = Price.of(tp2Val, currency).snapToTick(tickSize);
            Price tp3 = Price.of(tp3Val, currency).snapToTick(tickSize);

            List<SignalWindow> windows = watchlistFacade.getSignalWindows();
            Optional<Signal> priorActive = book.findActive(snap.symbol(), snap.timeframe());
            try {
                Optional<Signal> openedOpt = book.tryOpenOrReverse(
                        snap.symbol(),
                        snap.timeframe(),
                        dir,
                        entry,
                        sl,
                        List.of(tp1, tp2, tp3),
                        scoring.score(),
                        snap.regime(),
                        scoring.votes(),
                        strategy,
                        snap.closeTime(),
                        windows
                );
                if (openedOpt.isEmpty()) {
                    return Optional.empty();
                }
                Signal opened = openedOpt.get();
                signalsById.put(opened.id().value(), opened);

                if (priorActive.isPresent() && priorActive.get().state() == SignalState.INVALIDATED) {
                    Signal invalidated = priorActive.get();
                    SignalDto invDto = toDto(invalidated);
                    eventPublisher.publishEvent(new SignalInvalidated(invDto, opened.id().value()));
                    eventPublisher.publishEvent(new SignalClosed(invDto, 0.0));
                }

                SignalDto dto = toDto(opened);
                eventPublisher.publishEvent(new SignalOpened(dto));
                return Optional.of(dto);
            } catch (SignalWindowClosedException ex) {
                log.debug("Signal blocked by SignalWindow: {}", ex.getMessage());
                return Optional.empty();
            } catch (IllegalArgumentException ex) {
                log.debug("Signal invariant guard rejected candidate: {}", ex.getMessage());
                return Optional.empty();
            }
        }
    }

    private void publishTerminalOutcome(Signal signal, SignalState state) {
        SignalDto dto = toDto(signal);
        switch (state) {
            case TP_HIT -> {
                eventPublisher.publishEvent(new TakeProfitHit(dto, 1));
                eventPublisher.publishEvent(new SignalClosed(dto, signal.riskRewardTp1().ratio()));
            }
            case SL_HIT -> {
                eventPublisher.publishEvent(new StopLossHit(dto));
                eventPublisher.publishEvent(new SignalClosed(dto, -1.0));
            }
            case EXPIRED -> {
                eventPublisher.publishEvent(new SignalExpired(dto));
                eventPublisher.publishEvent(new SignalClosed(dto, 0.0));
            }
            default -> {}
        }
    }

    @Override
    public List<SignalDto> listRecentSignals(int limit) {
        return signalsById.values().stream()
                .sorted(Comparator.comparing(Signal::openedAt).reversed())
                .limit(limit)
                .map(SignalApplicationService::toDto)
                .toList();
    }

    @Override
    public List<SignalDto> listSignalsForSymbol(Symbol symbol) {
        return signalsById.values().stream()
                .filter(s -> s.symbol().equals(symbol))
                .sorted(Comparator.comparing(Signal::openedAt).reversed())
                .map(SignalApplicationService::toDto)
                .toList();
    }

    @Override
    public Optional<SignalDto> findSignalById(String signalId) {
        Signal s = signalsById.get(signalId);
        return s == null ? Optional.empty() : Optional.of(toDto(s));
    }

    @Override
    public void attachExplanation(String signalId, String explanation) {
        Signal s = signalsById.get(signalId);
        if (s != null) {
            s.attachExplanation(explanation);
        }
    }

    @Override
    public StrategySummaryDto getActiveStrategy() {
        Strategy s = this.activeStrategy;
        RiskPolicy rp = s.riskPolicy();
        return new StrategySummaryDto(
                s.strategyId(),
                s.name(),
                s.version(),
                rp.slAtrMultiplier(),
                rp.tp1RiskMultiple(),
                rp.tp2RiskMultiple(),
                rp.tp3RiskMultiple(),
                rp.minRiskReward(),
                rp.cooldownBars(),
                rp.maxHoldBars(),
                rp.minRelativeVolume()
        );
    }

    @Override
    public synchronized StrategySummaryDto updateStrategyParameters(
            double slAtrMultiplier,
            double tp1R,
            double tp2R,
            double tp3R,
            int cooldownBars,
            int maxHoldBars,
            String actor
    ) {
        RiskPolicy currentRp = activeStrategy.riskPolicy();
        RiskPolicy updatedRp = new RiskPolicy(
                slAtrMultiplier,
                tp1R,
                tp2R,
                tp3R,
                Math.min(tp1R, currentRp.minRiskReward()),
                cooldownBars,
                maxHoldBars,
                currentRp.minRelativeVolume()
        );
        this.activeStrategy = activeStrategy.withUpdatedConfiguration(
                activeStrategy.allRegimeWeights(),
                updatedRp
        );
        jdbcTemplate.update(
                "INSERT INTO strategy_audit_log (strategy_id, version, actor, action, created_at) VALUES (?, ?, ?, ?, ?)",
                activeStrategy.strategyId(),
                activeStrategy.version(),
                actor,
                "STRATEGY_VERSION_CREATED",
                Instant.now()
        );
        return getActiveStrategy();
    }

    public static SignalDto toDto(Signal s) {
        return new SignalDto(
                s.id().value(),
                s.symbol(),
                s.timeframe(),
                s.direction(),
                s.entry(),
                s.stopLoss(),
                s.tp1(),
                s.tp2(),
                s.tp3(),
                s.riskRewardTp1().ratio(),
                s.confidence().value(),
                s.confluenceScore().value(),
                s.regime(),
                s.rationale(),
                s.state().name(),
                s.state().labelTr(),
                s.strategyId(),
                s.strategyVersion(),
                s.openedAtBar(),
                s.openedAt(),
                s.closedAt().orElse(null),
                s.explanation().orElse(null)
        );
    }
}

