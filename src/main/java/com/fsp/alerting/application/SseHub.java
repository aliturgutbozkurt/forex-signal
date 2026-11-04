package com.fsp.alerting.application;

import com.fsp.ai.api.SignalExplained;
import com.fsp.alerting.api.AlertPayloadDto;
import com.fsp.alerting.api.AlertingFacade;
import com.fsp.alerting.domain.AlertDeliveryTracker;
import com.fsp.alerting.domain.VoicePayloadFactory;
import com.fsp.signal.api.SignalDto;
import com.fsp.signal.api.SignalOpened;
import com.fsp.watchlist.api.VoicePreferenceDto;
import com.fsp.watchlist.api.WatchlistFacade;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE broadcast hub and Last-Event-ID replay engine implementing {@code docs/tla/AlertDelivery.tla}
 * (SPEC-001 FR-07, FR-08, INV-ALR-1, LIV-ALR-1, ADR-0004).
 */
@NullMarked
@Service
public class SseHub implements AlertingFacade {

    private final WatchlistFacade watchlistFacade;
    private final VoicePayloadFactory voiceFactory = new VoicePayloadFactory();
    private final AlertDeliveryTracker deliveryTracker = new AlertDeliveryTracker();
    private final Map<String, AlertPayloadDto> publishedAlerts = new LinkedHashMap<>();
    private final List<SseEmitter> activeEmitters = new CopyOnWriteArrayList<>();

    public SseHub(WatchlistFacade watchlistFacade) {
        this.watchlistFacade = watchlistFacade;
    }

    @EventListener
    public void onSignalOpened(SignalOpened event) {
        SignalDto signal = event.signal();
        AlertPayloadDto payload = toAlertPayload(signal, null);
        synchronized (this) {
            deliveryTracker.commitToOutbox(signal.signalId());
            publishedAlerts.put(signal.signalId(), payload);
            broadcastSse("signal-opened", payload.signalId(), payload);
            deliveryTracker.markDispatchedAndCompleted(signal.signalId());
        }
    }

    @EventListener
    public void onSignalExplained(SignalExplained event) {
        synchronized (this) {
            AlertPayloadDto existing = publishedAlerts.get(event.signalId());
            if (existing != null) {
                AlertPayloadDto updated = new AlertPayloadDto(
                        existing.signalId(),
                        existing.symbol(),
                        existing.timeframe(),
                        existing.direction(),
                        existing.formattedPrice(),
                        existing.currencyUnitName(),
                        existing.spokenText(),
                        existing.spokenPhoneticTr(),
                        existing.chimePattern(),
                        existing.primaryFreqHz(),
                        existing.secondaryFreqHz(),
                        existing.voiceProfileName(),
                        existing.speechRate(),
                        existing.pitch(),
                        existing.confidence(),
                        existing.confluenceScore(),
                        event.explanationTr(),
                        existing.publishedAt()
                );
                publishedAlerts.put(event.signalId(), updated);
                broadcastSse("signal-explained", event.signalId(), updated);
            }
        }
    }

    public synchronized void commitPendingWithoutCompleting(SignalDto signal) {
        AlertPayloadDto payload = toAlertPayload(signal, null);
        deliveryTracker.commitToOutbox(signal.signalId());
        publishedAlerts.put(signal.signalId(), payload);
        deliveryTracker.markDispatchedOnly(signal.signalId());
    }

    @Override
    public synchronized List<AlertPayloadDto> simulateCrashAndRedeliverPending() {
        List<String> pendingIds = deliveryTracker.simulateServerCrashAndFindPendingOutbox();
        List<AlertPayloadDto> redelivered = new ArrayList<>(pendingIds.size());
        for (String id : pendingIds) {
            AlertPayloadDto payload = publishedAlerts.get(id);
            if (payload != null) {
                broadcastSse("signal-opened", id, payload);
                deliveryTracker.markDispatchedAndCompleted(id);
                redelivered.add(payload);
            }
        }
        return redelivered;
    }

    @Override
    public SseEmitter subscribe(@Nullable String clientId, @Nullable String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        activeEmitters.add(emitter);
        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));
        emitter.onError(_ -> activeEmitters.remove(emitter));

        String resolvedClientId = (clientId == null || clientId.isBlank()) ? "cli-" + UUID.randomUUID() : clientId;
        List<AlertPayloadDto> missed = replayMissedAlerts(lastEventId);
        for (AlertPayloadDto alert : missed) {
            try {
                emitter.send(SseEmitter.event()
                        .id(alert.signalId())
                        .name("signal-opened")
                        .data(alert));
                recordClientDeliveryAndDedup(resolvedClientId, alert.signalId());
            } catch (IOException _) {
                activeEmitters.remove(emitter);
                break;
            }
        }
        return emitter;
    }

    @Override
    public synchronized List<AlertPayloadDto> replayMissedAlerts(@Nullable String lastEventId) {
        List<String> ids = deliveryTracker.publishedSignalsAfter(lastEventId == null ? "" : lastEventId);
        List<AlertPayloadDto> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            AlertPayloadDto dto = publishedAlerts.get(id);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    @Override
    public boolean recordClientDeliveryAndDedup(String clientId, String signalId) {
        return deliveryTracker.deliverToClient(clientId, signalId);
    }

    @Override
    public int getClientSpokenCount(String clientId, String signalId) {
        return deliveryTracker.spokenCount(clientId, signalId);
    }

    @Override
    public AlertPayloadDto toAlertPayload(SignalDto signal, @Nullable String localeOverride) {
        VoicePreferenceDto vp = watchlistFacade.getVoicePreference();
        String locale = (localeOverride != null && !localeOverride.isBlank()) ? localeOverride : vp.locale();
        VoicePayloadFactory.VoiceNotificationPayload voice = voiceFactory.createPayload(
                signal.signalId(),
                signal.symbol(),
                signal.timeframe(),
                signal.direction(),
                signal.entry(),
                locale,
                vp.buyVoiceName(),
                vp.sellVoiceName(),
                vp.speechRate(),
                vp.buyPitch(),
                vp.sellPitch()
        );
        return new AlertPayloadDto(
                signal.signalId(),
                signal.symbol(),
                signal.timeframe(),
                signal.direction(),
                voice.formattedPrice(),
                voice.currencyUnitName(),
                voice.spokenText(),
                voice.spokenPhoneticTr(),
                voice.chimePattern(),
                voice.primaryFreqHz(),
                voice.secondaryFreqHz(),
                voice.voiceProfileName(),
                voice.speechRate(),
                voice.pitch(),
                signal.confidence(),
                signal.confluenceScore(),
                signal.explanation(),
                Instant.now()
        );
    }

    private void broadcastSse(String eventName, String eventId, AlertPayloadDto payload) {
        for (SseEmitter emitter : activeEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name(eventName)
                        .data(payload));
            } catch (IOException _) {
                activeEmitters.remove(emitter);
            }
        }
    }
}

