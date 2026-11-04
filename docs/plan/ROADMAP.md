# Roadmap — Spec-Driven Delivery Plan

Her issue bir spec ID'sine (FR-xx / INV-xx / NFR) bağlıdır. Akış: **Spec → TLA+ (gerekirse) → Issue → Test (kırmızı) → Kod → TLC + test yeşil → PR (spec referanslı)**.

```mermaid
gantt
  dateFormat YYYY-MM-DD
  title FSP Milestones
  section Foundation
  M0 Foundation & Spec       :m0, 2026-11-05, 7d
  section Core
  M1 Market Data             :m1, after m0, 10d
  M2 Technical Analysis      :m2, after m0, 12d
  M3 Signal Generation       :m3, after m2, 10d
  section Experience
  M4 Real-time UI & Voice    :m4, after m1, 14d
  M5 AI Insights             :m5, after m3, 8d
  section Proof
  M6 Performance & Backtest  :m6, after m3, 12d
  M7 Hardening & Release     :m7, after m6, 7d
```

| Milestone | Çıktı | Çıkış kapısı |
|---|---|---|
| M0 Foundation | Repo, CI (build + TLC + ArchUnit), Modulith iskelet, design tokens | `./mvnw verify` + `scripts/tlc.sh` yeşil |
| M1 Market Data | Instrument kataloğu, TradingView scanner + OHLCV adapter, BarSeries, BarClock | INV-BAR-1/2 property testleri |
| M2 Technical Analysis | Artımlı gösterge motoru (40+), rejim sınıflandırıcı, golden testler | JMH: < 5 µs / gösterge / bar |
| M3 Signal Generation | Strategy, ConfluenceScorer, Signal/SignalBook aggregate, windows, cooldown | INV-SIG-1..4 property testleri |
| M4 Real-time UI & Voice | Dashboard, enstrüman detay, watchlist, SSE hub, sesli bildirim, PWA | LIV/INV-ALR Playwright testleri, LCP < 1.2 sn |
| M5 AI Insights | Açıklama, sentiment, NL sorgu, MCP server | Faithfulness eval ≥ 0.9 |
| M6 Performance & Backtest | Backtest, walk-forward, Monte Carlo, paper trading, performans paneli | §9 metrikleri raporlanır |
| M7 Hardening | Gatling yük testi, güvenlik, AOT/CDS, Docker, gözlemlenebilirlik | NFR §7.5 eşikleri |

Issue'lar: [`scripts/create-issues.sh`](../../scripts/create-issues.sh) ile GitHub'a aktarılır.
