# Forex Signal Platform (FSP)

**Forex Emtiaları (18 Emtia) · Forex Pariteleri · ABD Hisseleri · Küresel Endeksler** için 40+ teknik gösterge (`O(1)` artımlı motor), hacim teyidi, piyasa rejimi sınıflandırması ve AI açıklamalarıyla (`XAI`) gerçek zamanlı **AL / SAT / TUT** kararları üreten, sinyalleri **farklı ton ve seslerle sözlü olarak anlık** okuyan, tek sayfadan yönetilen düşük gecikmeli analiz ve sinyal terminali.

> ⚠️ **Yasal Uyarı:** Bu sistem karar destek aracıdır, yatırım tavsiyesi değildir. Kazanç garanti edilmez — tüm strateji performans metrikleri yalnızca şeffaf backtest / walk-forward ve Monte Carlo simülasyon çıktılarından üretilir.

---

## Öne Çıkan Özellikler

- **Tek Sayfa Yatay Akordiyon Terminal (`%100` Tam Ekran, Yatay Scroll Yok):**
  - Ana ekranda **Enstrüman Kataloğu & Seans Durumu** sayfayı yatay sırayla tam genişlikte kaplar.
  - Her bir emtia veya parite akordiyon şeklinde açılır; varlık sınıfı filtresi (`Tümü`, `Forex Emtiaları (18)`, `Forex Pariteleri (8)`, `ABD Hisseleri (4)`, `Endeksler (2)`), hızlı arama (`⌘K`), toplu/tekil zaman dilimi seçimi, AI doğal dil sorgusu ve Walk-Forward Backtest tek sayfadan yönetilir.
- **Tüm 18 Forex Emtiası & Canlı TradingView Senkronizasyonu (BIST Yok):**
  - **Kıymetli Madenler (4):** `XAUUSD` (Spot Altın), `XAGUSD` (Spot Gümüş), `XPTUSD` (Spot Platin), `XPDUSD` (Spot Paladyum)
  - **Enerji (5):** `USOIL` (WTI Ham Petrol), `UKOIL` (Brent Ham Petrol), `NATGAS` (Doğalgaz), `GASOLINE` (RBOB Benzin), `HEATOIL` (Kalorifer Yakıtı)
  - **Endüstriyel Metaller (2):** `COPPER` (Bakır), `ALUMINUM` (Alüminyum)
  - **Tarım & Yumuşak Emtialar (7):** `WHEAT` (Buğday), `CORN` (Mısır), `SOYBN` (Soya Fasulyesi), `SUGAR` (Şeker No.11), `COFFEE` (Kahve Arabica), `COCOA` (Kakao), `COTTON` (Pamuk No.2)
  - **Forex Pariteleri (8):** `EURUSD`, `GBPUSD`, `USDJPY`, `USDCHF`, `AUDUSD`, `USDCAD`, `NZDUSD`, `USDTRY`
  - **ABD Hisseleri (4) & Küresel Endeksler (2):** `AAPL`, `NVDA`, `MSFT`, `TSLA`, `SPX500`, `NDX100`
- **10 Zaman Dilimi (`M1` – `MN`) & Gömülü TradingView Grafikleri:**
  - Tüm 10 zaman dilimi (`M1`, `M5`, `M15`, `M30`, `H1`, `H2`, `H4`, `D1`, `W1`, `MN`) hem enstrüman bazında hem global olarak seçilebilir.
  - Açılan her akordiyonun içinde seçili zaman dilimine senkron **TradingView Advanced Chart** ve **FSP Kapanmış Bar & EMA (9 / 21 / 50) + Hacim Grafiği** birlikte çalışır.
- **Dinamik `O(1)` 40+ Gösterge Motoru & Çoklu Zaman Dilimi `AL / SAT / TUT` Matrisi:**
  - Her ekranda ve her akordiyonda göstergelerin en üstünde toplam **AL**, **SAT**, **TUT** sayıları, 3 renkli oran çubuğu ve genel sentez kararı (`GÜÇLÜ AL` / `AL` / `SAT` / `GÜÇLÜ SAT` / `TUT`) gösterilir.
  - **40+ Gösterge Özeti (14 Gösterge Grubu):** `EMA 9/21/50`, `EMA 200/SMA 20/HMA 16`, `Supertrend (10,3)`, `Parabolic SAR`, `Ichimoku Tenkan/Kijun`, `ADX(14) (+DI/-DI)`, `RSI(14) & StochRSI`, `MACD / Sinyal / Hist`, `Stoch %K/%D & CCI(20)`, `Momentum(10) / AO / UO`, `Bollinger (20,2) & ATR(14)`, `VWAP & OBV`, `RVOL / CMF(20) / MFI(14)`, `Volume Profile POC & Pivot` satırlarının her birinde canlı değer ve `AL` / `SAT` / `TUT` rozeti yer alır; her `2.5` saniyede bir zamana bağlı dinamik güncellenir.
  - Her enstrümanın altında **Tüm Zaman Dilimleri (`M1`–`MN`) × Tüm Göstergeler `AL / SAT / TUT` Matrisi** ile 10 zaman diliminin tamamındaki gösterge kararları ve toplam `AL / SAT / TUT` sayıları tek tabloda sunulur.
- **No-Repaint & TLA+ ile Doğrulanmış Durum Makineleri:**
  - Yalnızca kapanmış bar (`BarClosed`) üzerinden sinyal üretilir (`INV-NR-1..3`).
  - Sinyal yaşam döngüsü (`SignalLifecycle`), sesli bildirim tekil teslimi (`AlertDelivery`) ve zaman penceresi / cooldown (`IndicatorWindowCooldown`) TLA+ modelleri TLC model checker ile doğrulanmıştır.

---

## Teknoloji Yığını & Mimari

| Katman | Seçim |
|---|---|
| **Dil / Runtime** | **Java 25**, Virtual Threads, Records, Sealed Hierarchies, JSpecify `@NullMarked` |
| **Framework** | **Spring Boot 4.1.x**, **Spring Modulith 2.1.x**, Spring Security (CSP, Passkey/WebAuthn) |
| **Mimari Doğrulama** | Hexagonal / DDD (`domain` paketleri saf Java, Spring bağımlılığı yasak), `ApplicationModules.verify()`, ArchUnit |
| **Arayüz (UI)** | Kurumsal Açık Tema (`data-theme="light"`, OKLCH tasarım sistemi), Thymeleaf, TradingView Widget + Lightweight Charts / High-DPI Canvas |
| **Ses & Bildirim** | SSE (`Last-Event-ID` replay), Web Audio API (AL yükselen üçgen ton, SAT alçalan testere ton) + Web Speech API (`tr-TR` / `en-US`) |
| **AI & MCP** | Halüsinasyon korumalı XAI sinyal açıklaması, doğal dil sorgu (`Tool Calling`) ve MCP araç uç noktaları (`/api/mcp/tools`, `/api/mcp/invoke`) |
| **Veri & Kalıcılık** | Flyway şema göçleri (`V1__initial_schema.sql`), H2 / PostgreSQL + TimescaleDB uyumlu şema |
| **Biçimsel Doğrulama & Test** | TLA+ (`tlc2.TLC`), JUnit 6, AssertJ, jqwik (Property-Based Testing), JMH mikro-benchmark |

### Modül Yapısı (`com.fsp`)
```text
com.fsp
├── FspApplication
├── shared          # Shared Kernel: Symbol, Timeframe, Price, Currency, AssetClass
├── marketdata      # TradingView Scanner batch istemcisi, OhlcvAdapter, BarClock, 18 Forex emtiası + Forex/Hisse/Endeks kataloğu
├── analysis        # Saf Java O(1) artımlı IndicatorEngine (40+ gösterge) & RegimeClassifier
├── signal          # ConfluenceScorer, Strategy sürümleme, No-Repaint Signal yaşam döngüsü, denetim izi (audit log)
├── performance     # Walk-Forward Backtest motoru, Monte Carlo (200 simülasyon), Paper-Trading portföy takibi
├── watchlist       # İzleme listesi, aktif zaman pencereleri, finansal takvim kara-liste filtresi
├── alerting        # SSE Hub (Last-Event-ID replay), VoicePayloadFactory (AL/SAT sesli bildirim)
├── ai              # XAI Türkçe sinyal açıklaması, doğal dil sorgu, MCP araç sunucusu
├── identity        # Passkey (WebAuthn) sınama-yanıt, RBAC, sıkı Content-Security-Policy
└── web             # Tek sayfa akordiyon terminal, enstrüman detay ve REST uç noktaları
```

---

## Hızlı Başlangıç

### 1. TLA+ Modellerini Doğrula
```bash
./scripts/tlc.sh
```

### 2. Testleri Çalıştır (Mimari, Modülerlik, Property-Based ve Entegrasyon Testleri)
```bash
./mvnw test
```

### 3. Uygulamayı Başlat
```bash
./mvnw spring-boot:run
```
Uygulama başladığında tarayıcınızdan aşağıdaki adreslere erişebilirsiniz:
- **Tek Sayfa Emtia & Forex Yönetim Terminali:** `http://localhost:8080/`
- **Enstrüman Tam Sayfa Grafik Görünümü:** `http://localhost:8080/instrument/XAUUSD?tf=M15`
- **Kurumsal Açık Tema Tasarım Sistemi:** `http://localhost:8080/styleguide`

---

## Temel REST & MCP Uç Noktaları

| Yöntem | Uç Nokta | Açıklama |
|---|---|---|
| `GET` | `/api/instruments?q=XAU` | Enstrüman kataloğunda sembol, ad veya varlık sınıfına göre `< 100 ms` arama |
| `GET` | `/api/signals` | Kapanmış barlardan üretilen No-Repaint sinyaller |
| `GET` | `/api/alerts/stream` | `Last-Event-ID` destekli gerçek zamanlı SSE sesli bildirim akışı |
| `GET` | `/api/ai/query?q=...` | Doğal dil ile emtia ve parite sorgulama (`Tool Calling`) |
| `GET` | `/api/backtest/{symbol}?tf=M15` | Walk-Forward Backtest + 200 Monte Carlo simülasyon raporu |
| `GET` | `/api/mcp/tools` | Kayıtlı MCP araçlarının şema listesi |
| `POST` | `/api/mcp/invoke?tool=...` | MCP aracı çalıştırma (`list_signals`, `get_snapshot`, `get_strategy_performance`) |

---

## Belgeler

| Belge | Açıklama |
|---|---|
| [docs/specs/SPEC-001-forex-signal-platform.md](docs/specs/SPEC-001-forex-signal-platform.md) | Tek doğruluk kaynağı (Single Source of Truth) sistem spesifikasyonu |
| [docs/tla/README.md](docs/tla/README.md) | TLA+ biçimsel modelleri (`BarClockNoRepaint`, `SignalLifecycle`, `AlertDelivery`, `IndicatorWindowCooldown`) |
| [docs/adr](docs/adr) | Mimari Karar Kayıtları (ADR-0001 .. ADR-0005) |
| [docs/plan/ROADMAP.md](docs/plan/ROADMAP.md) | Milestone (M0–M7) ve Issue (`#1`–`#41`) yol haritası |
| [AGENTS.md](AGENTS.md) | Spec-Driven + TLA+ çalışma kuralları |
