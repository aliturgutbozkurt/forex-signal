package com.fsp.alerting.api;

import com.fsp.signal.api.SignalDto;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AlertingFacade {

    SseEmitter subscribe(@Nullable String clientId, @Nullable String lastEventId);

    AlertPayloadDto toAlertPayload(SignalDto signal, @Nullable String localeOverride);

    List<AlertPayloadDto> replayMissedAlerts(@Nullable String lastEventId);

    boolean recordClientDeliveryAndDedup(String clientId, String signalId);

    int getClientSpokenCount(String clientId, String signalId);

    List<AlertPayloadDto> simulateCrashAndRedeliverPending();
}

