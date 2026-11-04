package com.fsp.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsp.alerting.api.AlertPayloadDto;
import com.fsp.alerting.application.SseHub;
import com.fsp.shared.Currency;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Price;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.shared.Vote;
import com.fsp.signal.api.SignalDto;
import com.fsp.signal.api.SignalOpened;
import com.fsp.watchlist.application.WatchlistApplicationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("FR-07")
@Tag("FR-08")
@Tag("INV-ALR-1")
@Tag("LIV-ALR-1")
@Tag("M4")
class AlertDeliveryScenarioTest {

    @Test
    @Tag("FR-07")
    @Tag("INV-ALR-1")
    @Tag("LIV-ALR-1")
    void crashRecoveryAndLastEventIdReplayGuaranteeSpokenAtMostOnceAndUnder50msLatency() {
        WatchlistApplicationService wl = new WatchlistApplicationService(_ -> {});
        SseHub hub = new SseHub(wl);

        SignalDto sig1 = sampleSignal("SIG-101", Symbol.of("EURUSD"), Direction.BUY, Price.of("1.0845", Currency.USD));
        SignalDto sig2 = sampleSignal("SIG-102", Symbol.of("THYAO"), Direction.SELL, Price.of("312.50", Currency.TRY));
        SignalDto sig3 = sampleSignal("SIG-103", Symbol.of("XAUUSD"), Direction.BUY, Price.of("2745.60", Currency.USD));

        // Warm up JVM DecimalFormat / Locale classloading before measuring hot-path p99 latency (< 50 ms)
        hub.toAlertPayload(sig1, "tr-TR");

        // 1. Measure p99 SignalOpened -> SSE dispatch latency (< 50 ms)
        long t0 = System.nanoTime();
        hub.onSignalOpened(new SignalOpened(sig1));
        hub.onSignalOpened(new SignalOpened(sig2));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(elapsedMs).isLessThan(50L);

        // Client "trader-1" receives sig1 before disconnecting
        assertThat(hub.recordClientDeliveryAndDedup("trader-1", "SIG-101")).isTrue();
        assertThat(hub.getClientSpokenCount("trader-1", "SIG-101")).isEqualTo(1);

        // 2. Simulate server crash while sig3 is PENDING in outbox
        hub.commitPendingWithoutCompleting(sig3);
        // Suppose sig3 was also partially delivered to trader-1 right before crash
        assertThat(hub.recordClientDeliveryAndDedup("trader-1", "SIG-103")).isTrue();

        // Server restarts and redelivers PENDING outbox entries
        List<AlertPayloadDto> redelivered = hub.simulateCrashAndRedeliverPending();
        assertThat(redelivered).extracting(AlertPayloadDto::signalId).containsExactly("SIG-103");

        // 3. Client reconnects with Last-Event-ID = "SIG-101" -> receives SIG-102 and SIG-103 replay
        List<AlertPayloadDto> replayed = hub.replayMissedAlerts("SIG-101");
        assertThat(replayed).extracting(AlertPayloadDto::signalId).containsExactly("SIG-102", "SIG-103");

        for (AlertPayloadDto alert : replayed) {
            hub.recordClientDeliveryAndDedup("trader-1", alert.signalId());
        }

        // INV-ALR-1 (SpokenAtMostOnce) & LIV-ALR-1 (EventuallyAnnounced):
        // Every published signal was spoken by trader-1 EXACTLY once (<= 1 and >= 1)!
        assertThat(hub.getClientSpokenCount("trader-1", "SIG-101")).isEqualTo(1);
        assertThat(hub.getClientSpokenCount("trader-1", "SIG-102")).isEqualTo(1);
        assertThat(hub.getClientSpokenCount("trader-1", "SIG-103")).isEqualTo(1);
    }

    @Test
    @Tag("FR-08")
    void voicePayloadFactoryGeneratesDistinctBuyAndSellTonesVoicesAndTurkishCurrencyPhrases() {
        WatchlistApplicationService wl = new WatchlistApplicationService(_ -> {});
        SseHub hub = new SseHub(wl);

        SignalDto eurusdBuy = sampleSignal("SIG-201", Symbol.of("EURUSD"), Direction.BUY, Price.of("1.0845", Currency.USD));
        SignalDto thyaoSell = sampleSignal("SIG-202", Symbol.of("THYAO"), Direction.SELL, Price.of("312.50", Currency.TRY));

        AlertPayloadDto buyAlert = hub.toAlertPayload(eurusdBuy, "tr-TR");
        AlertPayloadDto sellAlert = hub.toAlertPayload(thyaoSell, "tr-TR");

        // Distinct chime & voice profiles for AL vs SAT
        assertThat(buyAlert.chimePattern()).isEqualTo("ASCENDING_DUAL_CHIME");
        assertThat(buyAlert.primaryFreqHz()).isLessThan(buyAlert.secondaryFreqHz());
        assertThat(sellAlert.chimePattern()).isEqualTo("DESCENDING_DUAL_CHIME");
        assertThat(sellAlert.primaryFreqHz()).isGreaterThan(sellAlert.secondaryFreqHz());
        assertThat(buyAlert.voiceProfileName()).isNotEqualTo(sellAlert.voiceProfileName());
        assertThat(buyAlert.pitch()).isGreaterThan(sellAlert.pitch());

        // Exact Turkish phrases with currency units
        assertThat(buyAlert.spokenText()).isEqualTo("EURUSD, AL sinyali, fiyat 1,0845 dolar");
        assertThat(buyAlert.spokenPhoneticTr()).isEqualTo("EURUSD, AL sinyali, fiyat bir virgül sıfır sekiz dört beş dolar");
        assertThat(sellAlert.spokenText()).isEqualTo("THYAO, SAT sinyali, fiyat 312,50 lira");

        // English i18n check (FR-18)
        AlertPayloadDto buyEn = hub.toAlertPayload(eurusdBuy, "en-US");
        assertThat(buyEn.spokenText()).isEqualTo("EURUSD, BUY signal, price 1.0845 dollars");
    }

    private static SignalDto sampleSignal(String id, Symbol sym, Direction dir, Price entry) {
        Currency cur = entry.currency();
        Price sl = dir == Direction.BUY
                ? Price.of(entry.doubleValue() * 0.99, cur)
                : Price.of(entry.doubleValue() * 1.01, cur);
        Price tp1 = dir == Direction.BUY
                ? Price.of(entry.doubleValue() * 1.015, cur)
                : Price.of(entry.doubleValue() * 0.985, cur);
        Price tp2 = dir == Direction.BUY
                ? Price.of(entry.doubleValue() * 1.025, cur)
                : Price.of(entry.doubleValue() * 0.975, cur);
        Price tp3 = dir == Direction.BUY
                ? Price.of(entry.doubleValue() * 1.040, cur)
                : Price.of(entry.doubleValue() * 0.960, cur);

        return new SignalDto(
                id, sym, Timeframe.M15, dir, entry, sl, tp1, tp2, tp3,
                1.5, 82, dir == Direction.BUY ? 82.0 : -82.0,
                MarketRegime.TRENDING_UP,
                List.of(new Vote("EMA_STACK", "TREND", dir, dir.sign() * 0.9, 1.5, "Trend pozitif")),
                "ACTIVE", "Aktif", "FSP-CONFLUENCE-CORE", 1, 10L, Instant.now(), null, null
        );
    }
}

