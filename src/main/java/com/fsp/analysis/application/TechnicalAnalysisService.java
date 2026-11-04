package com.fsp.analysis.application;

import com.fsp.analysis.api.AnalysisFacade;
import com.fsp.analysis.api.IndicatorSnapshotComputed;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.analysis.api.MarketRegimeChanged;
import com.fsp.analysis.domain.BarWatermark;
import com.fsp.analysis.domain.IncrementalIndicatorState;
import com.fsp.marketdata.api.BarClosed;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class TechnicalAnalysisService implements AnalysisFacade {

    private final Map<String, IncrementalIndicatorState> states = new ConcurrentHashMap<>();
    private final Map<String, BarWatermark> watermarks = new ConcurrentHashMap<>();
    private final Map<String, IndicatorSnapshotDto> latestSnapshots = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public TechnicalAnalysisService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    @EventListener
    public IndicatorSnapshotDto onBarClosed(BarClosed bar) {
        String k = key(bar.symbol(), bar.timeframe());
        BarWatermark watermark = watermarks.computeIfAbsent(k, _ -> new BarWatermark());
        IncrementalIndicatorState state = states.computeIfAbsent(k, _ -> new IncrementalIndicatorState());

        synchronized (state) {
            if (!watermark.tryAdvance(bar.barIndex())) {
                IndicatorSnapshotDto existing = latestSnapshots.get(k);
                if (existing != null) {
                    return existing;
                }
            }
            MarketRegime previousRegime = state.currentRegime();
            state.step(bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), bar.newSessionDay());
            IndicatorSnapshotDto snapshot = state.toSnapshot(
                    bar.symbol(),
                    bar.timeframe(),
                    bar.barIndex(),
                    bar.closeTime()
            );
            latestSnapshots.put(k, snapshot);

            if (snapshot.regime() != previousRegime) {
                eventPublisher.publishEvent(new MarketRegimeChanged(
                        bar.symbol(),
                        bar.timeframe(),
                        previousRegime,
                        snapshot.regime(),
                        bar.closeTime()
                ));
            }
            eventPublisher.publishEvent(new IndicatorSnapshotComputed(snapshot));
            return snapshot;
        }
    }

    @Override
    public Optional<IndicatorSnapshotDto> getLatestSnapshot(Symbol symbol, Timeframe timeframe) {
        return Optional.ofNullable(latestSnapshots.get(key(symbol, timeframe)));
    }

    private static String key(Symbol symbol, Timeframe timeframe) {
        return symbol.value() + ":" + timeframe.name();
    }
}

