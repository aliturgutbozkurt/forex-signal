# ADR-0005 — AI Entegrasyonu: Spring AI, Açıklayıcı Rol, Yön Kararı Yok

- **Durum:** Accepted · 2026-11-04

## Karar
- Spring AI 2.0 `ChatClient`; varsayılan Google Gemini, Ollama lokal fallback (profil ile).
- Roller: (1) sinyal açıklaması (structured output, yalnızca rationale'daki göstergelere atıf), (2) haber/KAP sentiment (opsiyonel, ablation testiyle düşük ağırlıklı oy), (3) doğal dil sorgu (tool calling → read-model), (4) MCP server: harici AI ajanlarının sinyal sorgulaması.
- LLM **AL/SAT yönüne karar vermez**; deterministik motor karar verir.
- Guardrails: prompt-injection filtresi (haber metinleri), çıktı şema doğrulaması, eval seti (faithfulness), token bütçesi, cache, timeout → açıklamasız sinyal yine de yayınlanır (async `SignalExplained`).

## Sonuçlar
+ Gecikme kritik yolda LLM yok. + Tekrarlanabilir backtest. − Açıklama birkaç yüz ms sonra gelir (UI'da progressive).
