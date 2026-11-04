# TLA+ Modelleri — Planning-Driven Development

Her kritik eşzamanlılık/durum kuralı **kod yazılmadan önce** TLA+ ile modellenir ve TLC ile doğrulanır. Model değişmeden ilgili kod davranışı değişmez.

| Model | Kanıtlanan | Kod eşlemesi | Test eşlemesi |
|---|---|---|---|
| [SignalLifecycle.tla](SignalLifecycle.tla) | INV-SIG-1..4, AppendOnly | `signal.domain.Signal`, `signal.domain.SignalBook` | `SignalBookProperties` (jqwik) |
| [AlertDelivery.tla](AlertDelivery.tla) | INV-ALR-1 (en fazla 1 sesli okuma), LIV-ALR-1 (eninde sonunda teslim) | Modulith Event Publication Registry, `alerting.SseHub`, `static/js/voice.js` dedup | `AlertDeliveryScenarioTest`, Playwright reconnect testi |
| [BarIngestion.tla](BarIngestion.tla) | INV-BAR-1/2 (no-repaint, sıralı, boşluksuz), LIV-BAR-1 | `marketdata.domain.BarSeries`, `analysis.BarWatermark` | `BarSeriesProperties` |

## Çalıştırma
```bash
./scripts/tlc.sh                 # tümü
./scripts/tlc.sh AlertDelivery   # tek model
```

## Mutasyon kontrolü
Modellerin gerçekten hata yakaladığı doğrulanmıştır: `Deliver` içindeki istemci dedup'ı (`IF s \in seen[c]`) kaldırıldığında TLC `SpokenAtMostOnce` ihlalini (crash → yeniden dispatch → çift okuma) karşı örnekle bulur.

## Kurallar
- Spec'te yeni bir eşzamanlılık kuralı → önce `.tla` + `.cfg`, sonra issue, sonra kod.
- CI (`.github/workflows/tla.yml`) her PR'da TLC çalıştırır.
