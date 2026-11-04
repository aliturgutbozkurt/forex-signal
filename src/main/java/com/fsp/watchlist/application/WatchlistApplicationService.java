package com.fsp.watchlist.application;

import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.watchlist.api.SubscriptionDto;
import com.fsp.watchlist.api.VoicePreferenceDto;
import com.fsp.watchlist.api.WatchlistChanged;
import com.fsp.watchlist.api.WatchlistFacade;
import com.fsp.watchlist.domain.VoicePreference;
import com.fsp.watchlist.domain.Watchlist;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class WatchlistApplicationService implements WatchlistFacade {

    private final Watchlist defaultWatchlist = new Watchlist("default-trader");
    private final ApplicationEventPublisher eventPublisher;

    public WatchlistApplicationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        seedDefaultSubscriptions();
    }

    private void seedDefaultSubscriptions() {
        defaultWatchlist.subscribe(Symbol.of("XAUUSD"), EnumSet.of(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4));
        defaultWatchlist.subscribe(Symbol.of("XAGUSD"), EnumSet.of(Timeframe.M15, Timeframe.H1, Timeframe.H4));
        defaultWatchlist.subscribe(Symbol.of("USOIL"), EnumSet.of(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1));
        defaultWatchlist.subscribe(Symbol.of("UKOIL"), EnumSet.of(Timeframe.M15, Timeframe.H1));
        defaultWatchlist.subscribe(Symbol.of("NATGAS"), EnumSet.of(Timeframe.M15, Timeframe.H1));
        defaultWatchlist.subscribe(Symbol.of("COPPER"), EnumSet.of(Timeframe.H1, Timeframe.H4));
        defaultWatchlist.subscribe(Symbol.of("EURUSD"), EnumSet.of(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4));
        defaultWatchlist.subscribe(Symbol.of("GBPUSD"), EnumSet.of(Timeframe.M15, Timeframe.H1));
        defaultWatchlist.subscribe(Symbol.of("AAPL"), EnumSet.of(Timeframe.M15, Timeframe.H1, Timeframe.D1));
        defaultWatchlist.subscribe(Symbol.of("NVDA"), EnumSet.of(Timeframe.M15, Timeframe.H1));
    }

    @Override
    public synchronized List<SubscriptionDto> getSubscriptions() {
        return defaultWatchlist.subscriptions().stream()
                .map(s -> new SubscriptionDto(s.symbol(), s.timeframes()))
                .toList();
    }

    @Override
    public synchronized void addOrUpdateSubscription(Symbol symbol, Set<Timeframe> timeframes) {
        defaultWatchlist.subscribe(symbol, timeframes);
        publishChange();
    }

    @Override
    public synchronized void removeSubscription(Symbol symbol) {
        defaultWatchlist.unsubscribe(symbol);
        publishChange();
    }

    @Override
    public synchronized List<SignalWindow> getSignalWindows() {
        return defaultWatchlist.signalWindows();
    }

    @Override
    public synchronized void setSignalWindows(List<SignalWindow> windows) {
        defaultWatchlist.replaceSignalWindows(windows);
        publishChange();
    }

    @Override
    public synchronized boolean isSignalAllowedAt(Instant instant) {
        return defaultWatchlist.isAllowedAt(instant);
    }

    @Override
    public synchronized VoicePreferenceDto getVoicePreference() {
        VoicePreference vp = defaultWatchlist.voicePreference();
        return new VoicePreferenceDto(
                vp.enabled(),
                vp.locale(),
                vp.buyVoiceName(),
                vp.sellVoiceName(),
                vp.speechRate(),
                vp.buyPitch(),
                vp.sellPitch(),
                vp.quietHoursEnabled(),
                vp.quietFrom(),
                vp.quietTo()
        );
    }

    @Override
    public synchronized void updateVoicePreference(VoicePreferenceDto dto) {
        defaultWatchlist.updateVoicePreference(new VoicePreference(
                dto.enabled(),
                dto.locale(),
                dto.buyVoiceName(),
                dto.sellVoiceName(),
                dto.speechRate(),
                dto.buyPitch(),
                dto.sellPitch(),
                dto.quietHoursEnabled(),
                dto.quietFrom(),
                dto.quietTo()
        ));
    }

    private void publishChange() {
        eventPublisher.publishEvent(new WatchlistChanged(
                defaultWatchlist.userId(),
                getSubscriptions(),
                Instant.now()
        ));
    }
}

