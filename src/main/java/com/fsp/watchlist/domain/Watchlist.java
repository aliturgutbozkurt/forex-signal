package com.fsp.watchlist.domain;

import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.ddd.annotation.AggregateRoot;

@AggregateRoot
public final class Watchlist {

    private final String userId;
    private final Map<Symbol, Set<Timeframe>> subscriptions = new LinkedHashMap<>();
    private final List<SignalWindow> signalWindows = new ArrayList<>();
    private VoicePreference voicePreference;

    public Watchlist(String userId) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.voicePreference = VoicePreference.defaultTurkish();
        this.signalWindows.add(SignalWindow.alwaysOpen());
    }

    public String userId() {
        return userId;
    }

    public void subscribe(Symbol symbol, Set<Timeframe> timeframes) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeframes, "timeframes must not be null");
        if (timeframes.isEmpty()) {
            throw new IllegalArgumentException("At least one timeframe is required");
        }
        subscriptions.put(symbol, EnumSet.copyOf(timeframes));
    }

    public void unsubscribe(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        subscriptions.remove(symbol);
    }

    public List<Subscription> subscriptions() {
        return subscriptions.entrySet().stream()
                .map(e -> new Subscription(e.getKey(), e.getValue()))
                .toList();
    }

    public boolean isSubscribed(Symbol symbol, Timeframe timeframe) {
        Set<Timeframe> tfs = subscriptions.get(symbol);
        return tfs != null && tfs.contains(timeframe);
    }

    public List<SignalWindow> signalWindows() {
        return Collections.unmodifiableList(signalWindows);
    }

    public void replaceSignalWindows(List<SignalWindow> windows) {
        Objects.requireNonNull(windows, "windows must not be null");
        if (windows.isEmpty()) {
            throw new IllegalArgumentException("At least one SignalWindow is required");
        }
        signalWindows.clear();
        signalWindows.addAll(windows);
    }

    public boolean isAllowedAt(Instant instant) {
        return signalWindows.stream().anyMatch(w -> w.isAllowedAt(instant));
    }

    public VoicePreference voicePreference() {
        return voicePreference;
    }

    public void updateVoicePreference(VoicePreference newPreference) {
        this.voicePreference = Objects.requireNonNull(newPreference, "newPreference must not be null");
    }
}

