# ADR-0004 — At-least-once Teslim + İstemci Idempotency (Exactly-once Seslendirme)

- **Durum:** Accepted · 2026-11-04 · Model: [AlertDelivery.tla](../tla/AlertDelivery.tla)

## Karar
- `SignalOpened`, Spring Modulith JDBC Event Publication Registry ile aggregate ile aynı transaction'da kaydedilir; yeniden başlatmada tamamlanmamış yayınlar tekrar gönderilir.
- SSE event `id` = `signalId`; istemci `Last-Event-ID` ile yeniden bağlanır, sunucu replay eder.
- İstemci `signalId` setini IndexedDB'de tutar; görülmüş sinyali **seslendirmez**.

## Doğrulama
TLC: `SpokenAtMostOnce` (safety) ve `EventuallyAnnounced` (liveness, WF varsayımları altında) yeşil. Dedup kaldırıldığında karşı örnek bulunur.
