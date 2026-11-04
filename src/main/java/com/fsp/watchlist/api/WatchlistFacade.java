package com.fsp.watchlist.api;

import com.fsp.shared.SignalWindow;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface WatchlistFacade {

    List<SubscriptionDto> getSubscriptions();

    void addOrUpdateSubscription(Symbol symbol, Set<Timeframe> timeframes);

    void removeSubscription(Symbol symbol);

    List<SignalWindow> getSignalWindows();

    void setSignalWindows(List<SignalWindow> windows);

    boolean isSignalAllowedAt(Instant instant);

    VoicePreferenceDto getVoicePreference();

    void updateVoicePreference(VoicePreferenceDto preference);
}

