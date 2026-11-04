--------------------------- MODULE SharedHotCache ---------------------------
(***************************************************************************)
(* Tekil Toplayıcı (Single-Flight Collector) + Paylaşımlı Anlık Önbellek   *)
(* (Shared Hot Cache, Stale-While-Revalidate) ve Seans-Duyarlı Sinyal      *)
(* Sabitleme (Session-Aware Signal Freeze) protokolü.                      *)
(*                                                                         *)
(* Garanti edilen kurallar (SPEC-001 §6.3, §7.5):                          *)
(*  INV-CACHE-1  Dış veri kaynağına (TradingView Scanner) aynı anda en     *)
(*               fazla 1 tekil toplayıcı isteği (Single-Flight) çıkar.     *)
(*  INV-CACHE-2  Tüm istemciler dış kaynak yavaş/kapalı olsa dahi önbellek *)
(*               üzerinden her zaman geçerli veri okur (Stale-While-Rev.). *)
(*  INV-CACHE-3  Piyasa seansı kapalıyken (~sessionOpen[i]) fiyatlar ve    *)
(*               AL/SAT/TUT sinyalleri asla değişmez (%100 sabit kalır).   *)
(*  INV-CACHE-4  AL/SAT/TUT sinyal hesaplaması (signalVersion[i]) sentetik *)
(*               olarak asla değişmez; yalnızca seans açıkken ve gerçek    *)
(*               önbellek fiyatı değiştiğinde güncellenir.                 *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS Instruments,   \* ör. {XAUUSD, AAPL}
          Clients,       \* ör. {c1, c2}
          Prices,        \* ör. {1, 2}
          InitPrice,     \* başlangıç gerçek kapanış fiyatı
          MaxUpdates     \* model sınırı

ASSUME InitPrice \in Prices /\ MaxUpdates \in Nat \ {0}

VARIABLES sessionOpen,     \* [Instruments -> BOOLEAN] borsa seansı açık mı?
          extPrice,        \* [Instruments -> Prices] dış kaynaktaki gerçek fiyat
          inFlight,        \* 0..1: aktif Single-Flight dış kaynak isteği sayısı
          inFlightSnap,    \* [Instruments -> Prices] toplayıcının okuduğu anlık dış veri
          cachePrice,      \* [Instruments -> Prices] paylaşımlı anlık önbellek
          stale,           \* BOOLEAN: TTL süresi doldu mu?
          evaluatedPrice,  \* [Instruments -> Prices] sinyalin hesaplandığı son gerçek fiyat
          signalVersion,   \* [Instruments -> 0..MaxUpdates] AL/SAT/TUT hesaplama sürümü
          clientView       \* [Clients -> [Instruments -> Prices]] istemcilerin okuduğu veri

vars == <<sessionOpen, extPrice, inFlight, inFlightSnap, cachePrice,
          stale, evaluatedPrice, signalVersion, clientView>>

TypeOK ==
  /\ sessionOpen \in [Instruments -> BOOLEAN]
  /\ extPrice \in [Instruments -> Prices]
  /\ inFlight \in {0, 1}
  /\ inFlightSnap \in [Instruments -> Prices]
  /\ cachePrice \in [Instruments -> Prices]
  /\ stale \in BOOLEAN
  /\ evaluatedPrice \in [Instruments -> Prices]
  /\ signalVersion \in [Instruments -> 0..MaxUpdates]
  /\ clientView \in [Clients -> [Instruments -> Prices]]

Init ==
  /\ sessionOpen = [i \in Instruments |-> TRUE]
  /\ extPrice = [i \in Instruments |-> InitPrice]
  /\ inFlight = 0
  /\ inFlightSnap = [i \in Instruments |-> InitPrice]
  /\ cachePrice = [i \in Instruments |-> InitPrice]
  /\ stale = FALSE
  /\ evaluatedPrice = [i \in Instruments |-> InitPrice]
  /\ signalVersion = [i \in Instruments |-> 0]
  /\ clientView = [c \in Clients |-> [i \in Instruments |-> InitPrice]]

-----------------------------------------------------------------------------
\* Borsa seansı açılır veya kapanır (hafta sonu, günlük bakım arası, NASDAQ kapanışı).
ToggleSession(i) ==
  /\ sessionOpen' = [sessionOpen EXCEPT ![i] = ~@]
  /\ UNCHANGED <<extPrice, inFlight, inFlightSnap, cachePrice,
                 stale, evaluatedPrice, signalVersion, clientView>>

\* Dış kaynaktaki gerçek fiyat YALNIZCA ilgili piyasa seansı açıkken değişir.
ExternalTick(i, p) ==
  /\ sessionOpen[i]
  /\ p # extPrice[i]
  /\ extPrice' = [extPrice EXCEPT ![i] = p]
  /\ stale' = TRUE
  /\ UNCHANGED <<sessionOpen, inFlight, inFlightSnap, cachePrice,
                 evaluatedPrice, signalVersion, clientView>>

\* Önbellek TTL süresi dolduğunda arka plan yenilemesi için stale işaretlenir.
ExpireTtl ==
  /\ ~stale
  /\ stale' = TRUE
  /\ UNCHANGED <<sessionOpen, extPrice, inFlight, inFlightSnap, cachePrice,
                 evaluatedPrice, signalVersion, clientView>>

\* Single-Flight Collector: aynı anda yalnızca 1 toplayıcı dış kaynağa istek atabilir.
BeginSingleFlightFetch ==
  /\ inFlight = 0
  /\ stale
  /\ inFlight' = 1
  /\ inFlightSnap' = extPrice
  /\ UNCHANGED <<sessionOpen, extPrice, cachePrice, stale,
                 evaluatedPrice, signalVersion, clientView>>

\* Toplayıcı yanıtı alır ve yalnızca seansı AÇIK olan enstrümanların önbelleğini atomik günceller.
CompleteSingleFlightFetch ==
  /\ inFlight = 1
  /\ inFlight' = 0
  /\ stale' = FALSE
  /\ cachePrice' = [i \in Instruments |->
                      IF sessionOpen[i] THEN inFlightSnap[i] ELSE cachePrice[i]]
  /\ UNCHANGED <<sessionOpen, extPrice, inFlightSnap,
                 evaluatedPrice, signalVersion, clientView>>

\* Dış kaynak hatası/zaman aşımı: Stale-While-Revalidate ile mevcut önbellek korunur.
FailSingleFlightFetch ==
  /\ inFlight = 1
  /\ inFlight' = 0
  /\ UNCHANGED <<sessionOpen, extPrice, inFlightSnap, cachePrice,
                 stale, evaluatedPrice, signalVersion, clientView>>

\* İstemci her zaman beklemesiz (0 ms) paylaşımlı önbellekten okur; stale ise ve
\* aktif toplayıcı yoksa arka planda tekil toplayıcıyı tetikler.
ClientReadFromCache(c) ==
  /\ clientView' = [clientView EXCEPT ![c] = cachePrice]
  /\ IF stale /\ inFlight = 0
       THEN /\ inFlight' = 1
            /\ inFlightSnap' = extPrice
       ELSE UNCHANGED <<inFlight, inFlightSnap>>
  /\ UNCHANGED <<sessionOpen, extPrice, cachePrice, stale,
                 evaluatedPrice, signalVersion>>

\* Gösterge ve AL/SAT/TUT motoru: YALNIZCA seans açıkken ve gerçek önbellek fiyatı
\* değiştiğinde yeni sinyal/gösterge hesaplar (sentetik değişim yok, kapalı piyasada sabit).
EvaluateSignalsFromCache(i) ==
  /\ sessionOpen[i]
  /\ cachePrice[i] # evaluatedPrice[i]
  /\ signalVersion[i] < MaxUpdates
  /\ evaluatedPrice' = [evaluatedPrice EXCEPT ![i] = cachePrice[i]]
  /\ signalVersion' = [signalVersion EXCEPT ![i] = @ + 1]
  /\ UNCHANGED <<sessionOpen, extPrice, inFlight, inFlightSnap,
                 cachePrice, stale, clientView>>

Next ==
  \/ ExpireTtl
  \/ BeginSingleFlightFetch
  \/ CompleteSingleFlightFetch
  \/ FailSingleFlightFetch
  \/ \E i \in Instruments : ToggleSession(i) \/ EvaluateSignalsFromCache(i)
  \/ \E i \in Instruments, p \in Prices : ExternalTick(i, p)
  \/ \E c \in Clients : ClientReadFromCache(c)

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
\* INV-CACHE-1: Dış kaynağa aynı anda en fazla 1 tekil istek çıkar.
SingleFlightExclusivity == inFlight <= 1

\* INV-CACHE-2: Önbellek ve istemci görünümleri her zaman geçerli bir fiyat içerir.
CacheAlwaysValidAndPopulated ==
  /\ \A i \in Instruments : cachePrice[i] \in Prices /\ evaluatedPrice[i] \in Prices
  /\ \A c \in Clients, i \in Instruments : clientView[c][i] \in Prices

\* INV-CACHE-3: Seans kapalıyken fiyatlar ve AL/SAT/TUT sinyalleri asla değişmez.
ClosedMarketSignalsFrozen ==
  [][\A i \in Instruments :
       ~sessionOpen[i] =>
         /\ cachePrice'[i] = cachePrice[i]
         /\ evaluatedPrice'[i] = evaluatedPrice[i]
         /\ signalVersion'[i] = signalVersion[i]]_vars

\* INV-CACHE-4: Sinyaller sentetik olarak değişmez; yalnızca açık seansta gerçek fiyat değişimiyle güncellenir.
SignalChangesOnlyOnRealOpenPriceMove ==
  [][\A i \in Instruments :
       (signalVersion'[i] # signalVersion[i]) =>
         /\ sessionOpen[i]
         /\ cachePrice[i] # evaluatedPrice[i]
         /\ evaluatedPrice'[i] = cachePrice[i]]_vars
=============================================================================

