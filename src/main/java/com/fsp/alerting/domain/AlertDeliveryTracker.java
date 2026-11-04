package com.fsp.alerting.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure domain state machine implementing {@code docs/tla/AlertDelivery.tla} (SPEC-001 §6.3, ADR-0004):
 * <ul>
 *   <li><b>INV-ALR-1 (SpokenAtMostOnce):</b> {@code spoken[client][signal] <= 1} across crashes, duplicate dispatches, and reconnects.</li>
 *   <li><b>LIV-ALR-1 (EventuallyAnnounced):</b> Every published signal in {@code outbox} reaches every reconnected client.</li>
 *   <li><b>OutboxMonotonic:</b> Outbox state transitions monotonically {@code NONE -> PENDING -> COMPLETED}.</li>
 * </ul>
 */
public final class AlertDeliveryTracker {

    public enum OutboxState {
        NONE,
        PENDING,
        COMPLETED
    }

    private final Map<String, OutboxState> outbox = new LinkedHashMap<>();
    private final Set<String> dispatchedInCurrentProcess = new HashSet<>();
    private final Map<String, Set<String>> clientSeen = new HashMap<>();
    private final Map<String, Map<String, Integer>> clientSpokenCount = new HashMap<>();

    /**
     * TLA+ {@code Commit(s)}: records signal in persistent outbox as PENDING.
     */
    public synchronized void commitToOutbox(String signalId) {
        Objects.requireNonNull(signalId);
        OutboxState current = outbox.getOrDefault(signalId, OutboxState.NONE);
        if (current == OutboxState.NONE) {
            outbox.put(signalId, OutboxState.PENDING);
        }
    }

    /**
     * TLA+ {@code Dispatch(s)} & {@code Complete(s)}.
     */
    public synchronized void markDispatchedAndCompleted(String signalId) {
        Objects.requireNonNull(signalId);
        OutboxState current = outbox.getOrDefault(signalId, OutboxState.NONE);
        if (current == OutboxState.PENDING) {
            dispatchedInCurrentProcess.add(signalId);
            outbox.put(signalId, OutboxState.COMPLETED);
        }
    }

    public synchronized void markDispatchedOnly(String signalId) {
        Objects.requireNonNull(signalId);
        if (outbox.getOrDefault(signalId, OutboxState.NONE) == OutboxState.PENDING) {
            dispatchedInCurrentProcess.add(signalId);
        }
    }

    /**
     * TLA+ {@code Crash}: volatile process state ({@code dispatched}) is lost while {@code outbox} and client {@code seen} persist.
     */
    public synchronized List<String> simulateServerCrashAndFindPendingOutbox() {
        dispatchedInCurrentProcess.clear();
        return outbox.entrySet().stream()
                .filter(e -> e.getValue() == OutboxState.PENDING)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * TLA+ {@code Reconnect(c)} with {@code Last-Event-ID}: returns all published signal IDs after {@code lastEventId}.
     */
    public synchronized List<String> publishedSignalsAfter(String lastEventId) {
        List<String> allPublished = outbox.entrySet().stream()
                .filter(e -> e.getValue() != OutboxState.NONE)
                .map(Map.Entry::getKey)
                .toList();
        if (lastEventId == null || lastEventId.isBlank()) {
            return allPublished;
        }
        int idx = allPublished.indexOf(lastEventId);
        if (idx < 0) {
            return allPublished;
        }
        return allPublished.subList(idx + 1, allPublished.size());
    }

    /**
     * TLA+ {@code Deliver(s, c)}: client receives signal and speaks it iff {@code s not in seen[c]}.
     * Returns true if spoken, false if deduplicated.
     */
    public synchronized boolean deliverToClient(String clientId, String signalId) {
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(signalId);
        Set<String> seen = clientSeen.computeIfAbsent(clientId, _ -> new HashSet<>());
        Map<String, Integer> spoken = clientSpokenCount.computeIfAbsent(clientId, _ -> new HashMap<>());
        if (seen.contains(signalId)) {
            return false;
        }
        seen.add(signalId);
        spoken.merge(signalId, 1, Integer::sum);
        return true;
    }

    public synchronized int spokenCount(String clientId, String signalId) {
        return clientSpokenCount.getOrDefault(clientId, Collections.emptyMap()).getOrDefault(signalId, 0);
    }

    public synchronized boolean hasClientSeen(String clientId, String signalId) {
        return clientSeen.getOrDefault(clientId, Collections.emptySet()).contains(signalId);
    }

    public synchronized OutboxState outboxState(String signalId) {
        return outbox.getOrDefault(signalId, OutboxState.NONE);
    }
}

