#!/usr/bin/env bash
# SPEC-001'den GitHub label, milestone ve issue'larını oluşturur.
# Kullanım: ./scripts/create-issues.sh   (gh auth login yapılmış olmalı, repo dizininde)
set -euo pipefail
GH="${GH:-gh}"
REPO="$($GH repo view --json nameWithOwner -q .nameWithOwner)"

label() { $GH label create "$1" --color "$2" --description "${3:-}" --force >/dev/null; }
milestone() {
  $GH api "repos/$REPO/milestones" -q ".[] | select(.title==\"$1\") | .number" | grep -q . ||
  $GH api "repos/$REPO/milestones" -f title="$1" -f description="$2" >/dev/null
}
issue() { # title milestone labels body
  if $GH issue list --state all --search "\"$1\" in:title" --json title -q '.[].title' | grep -Fxq "$1"; then
    echo "skip: $1"; return; fi
  $GH issue create --title "$1" --milestone "$2" --label "$3" --body "$4" >/dev/null && echo "ok:   $1"
}
dod='
### Definition of Done
- [ ] Kabul kriterleri `@Tag` ile testlenmiş (önce kırmızı)
- [ ] `./mvnw verify` (Modulith verify + ArchUnit) yeşil
- [ ] TLA+ etkisi varsa `./scripts/tlc.sh` yeşil
- [ ] Spec güncel'

# ---------- labels ----------
label "type:feature" 1f883d; label "type:spec" 8250df "Spec / TLA+ / ADR"; label "type:infra" 6e7781
label "type:perf" d93f0b; label "type:ux" e99695; label "type:ai" 0e8a16; label "type:test" fbca04
for c in marketdata analysis signal performance watchlist alerting ai identity web shared; do label "ctx:$c" c5def5 "Bounded context"; done
label "tla+" 5319e7 "TLA+ modeli ile bağlı"; label "priority:P0" b60205; label "priority:P1" d93f0b; label "priority:P2" fbca04

# ---------- milestones ----------
milestone "M0 Foundation"            "Repo, CI, Modulith iskelet, design system"
milestone "M1 Market Data"           "Instrument, adapters, BarSeries, BarClock"
milestone "M2 Technical Analysis"    "Artımlı gösterge motoru + rejim"
milestone "M3 Signal Generation"     "Strategy, Confluence, Signal aggregate"
milestone "M4 Real-time UI & Voice"  "Thymeleaf/HTMX UI, SSE, sesli bildirim"
milestone "M5 AI Insights"           "Spring AI açıklama, sentiment, NL sorgu, MCP"
milestone "M6 Performance & Backtest" "Backtest, walk-forward, paper trading"
milestone "M7 Hardening & Release"   "Yük, güvenlik, AOT, gözlemlenebilirlik"

M0="M0 Foundation"; M1="M1 Market Data"; M2="M2 Technical Analysis"; M3="M3 Signal Generation"
M4="M4 Real-time UI & Voice"; M5="M5 AI Insights"; M6="M6 Performance & Backtest"; M7="M7 Hardening & Release"

# ---------- M0 ----------
issue "[SPEC] SPEC-001 review & sign-off" "$M0" "type:spec,priority:P0" "Spec: SPEC-001 tümü
- [ ] Bounded context haritası onayı (§5)
- [ ] FR/NFR tablosu onayı (§7.5, §8, §9)
- [ ] Açık sorular (§13) karara bağlandı
$dod"
issue "[TLA+] SignalLifecycle, AlertDelivery, BarIngestion modelleri + CI" "$M0" "type:spec,tla+,priority:P0" "Spec: §6.3, ADR-0004
- [x] Üç model yazıldı, TLC yeşil, mutasyon kontrolü yapıldı
- [ ] \`.github/workflows/tla.yml\` ilk PR'da yeşil
- [ ] Model ↔ kod eşlemesi docs/tla/README.md'de
$dod"
issue "Spring Modulith iskeleti (Java 25, Boot 4.1, Modulith 2.1)" "$M0" "type:infra,ctx:shared,priority:P0" "Spec: §7.1, §7.2, ADR-0001
**Kabul:** \`com.fsp\` altında 10 modül paketi; \`ModularityTests\` içinde \`ApplicationModules.verify()\` + \`Documenter\` (C4 + modül canvas) çalışır; Maven wrapper; virtual threads açık.
$dod"
issue "ArchUnit + jMolecules mimari kuralları" "$M0" "type:test,ctx:shared,priority:P0" "Spec: §7.2, AGENTS.md#5
**Kabul:** domain → Spring bağımlılığı yasak; adapter → domain tek yön; @AggregateRoot yalnızca domain paketinde; ihlal CI'ı kırar.
$dod"
issue "Shared kernel value object'leri (Price, Currency, Timeframe, Symbol, Percentage)" "$M0" "type:feature,ctx:shared,priority:P0" "Spec: §6.2
**Kabul:** record + compact constructor doğrulaması; jqwik property testleri (ör. Price aritmetiği birim uyumsuzluğunda exception); Timeframe bar sınırı hesaplama (TZ'li).
$dod"
issue "CI pipeline: build, test, Modulith verify, OWASP, SBOM" "$M0" "type:infra,priority:P0" "Spec: §11, §12
**Kabul:** GitHub Actions: \`./mvnw verify\`, Testcontainers, CycloneDX SBOM, dependency-check; PR'da zorunlu check.
$dod"
issue "Design system: OKLCH token'ları, tipografi, glass kartlar, motion" "$M0" "type:ux,ctx:web,priority:P1" "Spec: §10
**Kabul:** \`static/css/tokens.css\`, \`@layer\` yapısı, dark/light, AL/SAT renk token'ları, reduced-motion; stil rehberi sayfası \`/styleguide\`.
$dod"
issue "PostgreSQL 18 + TimescaleDB + Flyway + docker compose" "$M0" "type:infra,priority:P0" "Spec: §7.1
**Kabul:** \`compose.yaml\` (Postgres+Timescale, Valkey); Spring Boot docker-compose desteği; bars hypertable migration.
$dod"

# ---------- M1 ----------
issue "Instrument aggregate + katalog (Forex, US, BIST, emtia, endeks)" "$M1" "type:feature,ctx:marketdata,priority:P0" "Spec: FR-01, §6.1
**Kabul:** Given 'THY' When arama Then THYAO (BIST, TRY, seans 10:00–18:00 Europe/Istanbul) < 100 ms. TradingSession tatil takvimi.
$dod"
issue "TradingViewScannerAdapter (IndicatorSnapshotPort) — batch, CB, rate limit" "$M1" "type:feature,ctx:marketdata,priority:P0" "Spec: §4, ADR-0002
**Kabul:** forex/america/turkey/cfd market'leri; 500 sembol tek batch; Resilience4j CB + rate limiter + jitter retry; WireMock contract testleri; timeframe sütun eşlemesi (\`RSI|15\` vb.).
$dod"
issue "OhlcvAdapter (MarketDataPort) + backfill" "$M1" "type:feature,ctx:marketdata,tla+,priority:P0" "Spec: §4, INV-BAR-1/2, BarIngestion.tla
**Kabul:** Eksik bar tespitinde backfill (\`Backfill\` aksiyonu); oluşan bar asla yayınlanmaz.
$dod"
issue "BarSeries aggregate + watermark (no-repaint)" "$M1" "type:feature,ctx:marketdata,tla+,priority:P0" "Spec: INV-BAR-1, INV-BAR-2, LIV-BAR-1
**Kabul:** jqwik: rastgele sırasız/tekrarlı/eksik bar akışında değerlendirme sırası = 0,1,2,… ve hepsi kapanmış. Primitive ring buffer.
$dod"
issue "BarClock: timeframe sınırında BarClosed yayını (virtual threads, StructuredTaskScope)" "$M1" "type:perf,ctx:marketdata,priority:P0" "Spec: §7.3, §7.5
**Kabul:** 500×3 TF tarama < 2 sn; deadline aşan fetch iptal; \`BarClosed\` Modulith event.
$dod"
issue "Data source health & degraded mode" "$M1" "type:feature,ctx:marketdata,priority:P1" "Spec: FR-17
**Kabul:** CB açık → \`DataSourceDegraded\` event → UI rozeti; scanner–OHLCV sapma toleransı.
$dod"

# ---------- M2 ----------
issue "Artımlı gösterge motoru: Trend grubu (EMA/SMA/HMA/Ichimoku/Supertrend/PSAR/ADX)" "$M2" "type:feature,ctx:analysis,priority:P0" "Spec: §7.4
**Kabul:** TradingView değerleriyle golden test (tolerans 1e-6); O(1)/bar; JMH < 5 µs.
$dod"
issue "Momentum grubu (RSI, Stoch, StochRSI, MACD, CCI, W%R, AO, UO)" "$M2" "type:feature,ctx:analysis,priority:P0" "Spec: §7.4
**Kabul:** golden testler + divergence dedektörü.
$dod"
issue "Volatilite + Hacim grubu (ATR, BB, Keltner, Donchian, OBV, VWAP, CMF, MFI, RVOL, Volume Profile)" "$M2" "type:feature,ctx:analysis,priority:P0" "Spec: §7.4
**Kabul:** golden testler; VWAP seans resetinde TradingSession kullanır.
$dod"
issue "Pivot, destek/direnç, swing yapısı" "$M2" "type:feature,ctx:analysis,priority:P1" "Spec: §7.4
**Kabul:** Classic/Fibonacci/Camarilla; SL/TP seviye yapıştırma için API.
$dod"
issue "MarketRegime sınıflandırıcı" "$M2" "type:feature,ctx:analysis,priority:P0" "Spec: §5.1 Market Regime
**Kabul:** ADX, ATR%, BB width ile TRENDING_UP/DOWN, RANGING, VOLATILE; histerezis ile titreme yok; \`MarketRegimeChanged\` event.
$dod"

# ---------- M3 ----------
issue "Strategy aggregate + VoteRule'lar + rejim ağırlıkları (sürümlü)" "$M3" "type:feature,ctx:signal,priority:P0" "Spec: FR-16, §6.1
**Kabul:** Değişiklik yeni sürüm üretir; geçmiş sinyaller sürüm referansı taşır.
$dod"
issue "ConfluenceScorer (MTF + hacim teyidi)" "$M3" "type:feature,ctx:signal,priority:P0" "Spec: FR-04, §7.4
**Kabul:** Snapshot fixture → deterministik skor (golden); MTF uyumsuzluğunda skor cezalandırılır.
$dod"
issue "Signal aggregate + SignalBook (INV-SIG-1..4)" "$M3" "type:feature,ctx:signal,tla+,priority:P0" "Spec: FR-05, FR-06, §6.3, SignalLifecycle.tla
**Kabul:** jqwik stateful test, TLA+ aksiyonlarını (Open/Reverse/Resolve/Expire/CloseBar) birebir uygular; tüm invariantlar her adımda kontrol edilir. ATR tabanlı SL/TP, R:R guard.
$dod"
issue "SignalWindow (seans/gün/saat, TZ) uygulaması" "$M3" "type:feature,ctx:signal,ctx:watchlist,priority:P0" "Spec: FR-03
**Kabul:** Pencere dışında \`open()\` reddedilir; DST geçişleri testli.
$dod"

# ---------- M4 ----------
issue "Watchlist & tercihler (enstrüman × timeframe, pencereler, ses)" "$M4" "type:feature,ctx:watchlist,priority:P0" "Spec: FR-02, FR-08
**Kabul:** HTMX ile CRUD; \`WatchlistChanged\` → tarama planı güncellenir.
$dod"
issue "SseHub + Last-Event-ID replay (Modulith outbox)" "$M4" "type:feature,ctx:alerting,tla+,priority:P0" "Spec: FR-07, ADR-0004, AlertDelivery.tla
**Kabul:** \`@ApplicationModuleTest\` Scenario: crash simülasyonunda yeniden yayın; replay; p99 SignalOpened→SSE < 50 ms.
$dod"
issue "Sesli bildirim: AL/SAT farklı ton + ses, fiyatı birimiyle Türkçe okuma" "$M4" "type:ux,ctx:alerting,ctx:web,tla+,priority:P0" "Spec: FR-08, INV-ALR-1, ADR-0003
**Kabul:**
- AL: yükselen çift ton + ses A; SAT: alçalan ton + ses B (seçilebilir)
- 'EURUSD, AL sinyali, fiyat bir virgül sıfır sekiz dört beş dolar' / 'THYAO, SAT, 312,50 lira' (Intl.NumberFormat + birim sözlüğü)
- IndexedDB signalId dedup → aynı sinyal 2. kez okunmaz (Playwright reconnect testi)
- Sessiz saatler, hız/ton, test butonu, autoplay unlock
$dod"
issue "Dashboard: canlı sinyal akışı, heatmap, komut paleti" "$M4" "type:ux,ctx:web,priority:P0" "Spec: §10
**Kabul:** LCP < 1.2 sn, INP < 100 ms; View Transitions; skeleton; ⌘K.
$dod"
issue "Enstrüman detay: Lightweight Charts, sinyal marker, SL/TP çizgileri, gösterge paneli" "$M4" "type:ux,ctx:web,priority:P0" "Spec: FR-14
**Kabul:** 10k bar 60 fps; canlı güncelleme SSE ile.
$dod"
issue "Identity: passkey (WebAuthn) + OAuth2 login" "$M4" "type:feature,ctx:identity,priority:P1" "Spec: FR-15, §11
$dod"
issue "PWA + Web Push (sekme kapalıyken bildirim)" "$M4" "type:feature,ctx:web,priority:P2" "Spec: §10, Q3
$dod"
issue "i18n (tr, en) — UI ve ses metinleri" "$M4" "type:feature,ctx:web,priority:P1" "Spec: FR-18
$dod"

# ---------- M5 ----------
issue "AI sinyal açıklaması (Spring AI, structured output, faithfulness guard)" "$M5" "type:ai,ctx:ai,priority:P0" "Spec: FR-09, ADR-0005
**Kabul:** Async \`SignalExplained\`; yalnızca rationale göstergelerine atıf (eval ≥ 0.9); timeout'ta sinyal açıklamasız yayınlanır.
$dod"
issue "Haber/KAP sentiment (prompt-injection guard, ablation)" "$M5" "type:ai,ctx:ai,priority:P2" "Spec: ADR-0005
**Kabul:** Sentiment oyu ablation testinde PF'yi düşürüyorsa ağırlık 0.
$dod"
issue "Doğal dil sorgu (tool calling) + MCP server" "$M5" "type:ai,ctx:ai,priority:P1" "Spec: FR-10
**Kabul:** 'Bugün BIST'te en güçlü AL sinyalleri?' → read-model tool çağrısı, kaynaklı yanıt; MCP tools: list_signals, explain_signal.
$dod"

# ---------- M6 ----------
issue "Backtest motoru (look-ahead free, maliyet modeli)" "$M6" "type:feature,ctx:performance,priority:P0" "Spec: FR-12, §9
**Kabul:** Shift testi (gelecek bar sızıntısı yok); spread+komisyon+slippage; canlı motorla aynı kod yolu.
$dod"
issue "Walk-forward optimizasyon + Monte Carlo" "$M6" "type:feature,ctx:performance,priority:P0" "Spec: §9
**Kabul:** IS→OOS PF bozulması raporlanır; ≥200 işlem kuralı; 'experimental' etiketi otomatik.
$dod"
issue "Paper trading + performans read-model ve paneli" "$M6" "type:feature,ctx:performance,ctx:web,priority:P0" "Spec: FR-11, FR-13
**Kabul:** Win rate, PF, expectancy, max DD, Sharpe/Sortino strateji/varlık/TF kırılımında; equity eğrisi.
$dod"

# ---------- M7 ----------
issue "Performans: JMH + Gatling eşikleri CI'da" "$M7" "type:perf,priority:P0" "Spec: §7.5
$dod"
issue "Gözlemlenebilirlik: OpenTelemetry, Modulith observability, Grafana dashboard" "$M7" "type:infra,priority:P1" "Spec: §7.1
$dod"
issue "AOT cache/CDS, distroless Docker image, GraalVM native profili" "$M7" "type:infra,type:perf,priority:P1" "Spec: §7.5 başlangıç < 1.5 sn
$dod"
issue "Güvenlik sertleştirme: CSP nonce, rate limit, ASVS L2 kontrol listesi" "$M7" "type:infra,priority:P0" "Spec: §11
$dod"
echo "Done."
