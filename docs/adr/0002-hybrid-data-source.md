# ADR-0002 — Hibrit Veri Kaynağı: TradingView Scanner + Kendi Gösterge Motoru

- **Durum:** Accepted · 2026-11-04

## Bağlam
TradingView'in resmî, ücretsiz bir sinyal API'si yoktur. `scanner.tradingview.com/{market}/scan` resmî olmayan, ücretsiz bir uç noktadır (göstergeler + `Recommend.*`), rate-limit ve ToS riski taşır. Webhook alert'leri ücretli plan gerektirir.

## Karar
1. `IndicatorSnapshotPort` → **TradingViewScannerAdapter** (birincil, toplu istek, Resilience4j CB + rate limiter, 1 sn TTL cache).
2. `MarketDataPort` → **OhlcvAdapter** (ham bar) + saf Java **IndicatorEngine** (artımlı hesaplama) ile çift doğrulama ve ek göstergeler.
3. İki kaynak arasında sapma > tolerans → `DataQualityDegraded` event'i, snapshot "low confidence".
4. Scanner devre dışı → degraded mode: yalnızca kendi motor.

## Sonuçlar
+ Tek kaynağa bağımlılık yok; deterministik backtest (kendi motorumuz).
− Ek hesaplama maliyeti (artımlı algoritmalarla ihmal edilebilir).
− Ticari kullanımda lisanslı sağlayıcıya geçiş gerekebilir (adapter değişimi yeterli).
