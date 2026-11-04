# ADR-0003 — UI: Thymeleaf + HTMX + SSE, Tarayıcı Tarafı Ses

- **Durum:** Accepted · 2026-11-04

## Karar
- Sunucu taraflı render: Thymeleaf 3.1 fragment'ları + HTMX 2 (SSE extension) + Alpine.js küçük etkileşimler için.
- Vanilla CSS design system (OKLCH token'ları, container queries, View Transitions API, `@layer`).
- Grafik: TradingView Lightweight Charts (Apache-2.0).
- Ses: Web Speech API (`speechSynthesis`, tr-TR/en-US) + Web Audio API ile AL (yükselen) / SAT (alçalan) farklı tonlar; sesler kullanıcı tarafından seçilebilir. Sunucu maliyeti ve ağ gecikmesi sıfır.

## Sonuçlar
+ SPA build zinciri yok, düşük JS, hızlı LCP; SSE ile tek yönlü düşük gecikmeli push.
− Ses kalitesi işletim sistemi seslerine bağlı (opsiyonel sunucu neural TTS adapter'ı ileride).
