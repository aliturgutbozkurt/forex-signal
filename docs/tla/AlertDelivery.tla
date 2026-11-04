--------------------------- MODULE AlertDelivery ---------------------------
(***************************************************************************)
(* Sinyal -> anlık bildirim (SSE + sesli okuma) teslim protokolü.          *)
(*                                                                         *)
(* Mimari varsayımlar (SPEC-001 §7.4, ADR-0004):                           *)
(*  - SignalOpened domain event'i, Spring Modulith Event Publication       *)
(*    Registry (transactional outbox) ile aggregate ile aynı TX'te yazılır.*)
(*  - Dispatcher at-least-once teslim eder; sunucu çökerse PENDING         *)
(*    kayıtlar yeniden yayınlanır (duplicate olabilir).                    *)
(*  - SSE istemcisi Last-Event-ID ile yeniden bağlanır, sunucu replay eder.*)
(*  - İstemci signalId ile idempotent dedup yapar (IndexedDB).             *)
(*                                                                         *)
(* Kanıtlanan özellikler:                                                  *)
(*  INV-ALR-1  Her istemci her sinyali EN FAZLA bir kez sesli okur.        *)
(*  LIV-ALR-1  Yayınlanan her sinyal, her istemciye EN AZ bir kez ulaşır   *)
(*             (sınırlı sayıda kopma/çökme varsayımı altında).             *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS Signals, Clients, MaxCrashes, MaxDrops

VARIABLES outbox,      \* [Signals -> {"NONE","PENDING","COMPLETED"}]
          dispatched,  \* bu süreç ömründe dispatch edilmiş sinyaller
          inflight,    \* ağdaki mesajlar: <<signal, client>>
          connected,   \* [Clients -> BOOLEAN]
          seen,        \* [Clients -> SUBSET Signals]  (istemci dedup deposu)
          spoken,      \* [Clients -> [Signals -> Nat]] sesli okuma sayacı
          crashes, drops

vars == <<outbox, dispatched, inflight, connected, seen, spoken, crashes, drops>>

TypeOK ==
  /\ outbox \in [Signals -> {"NONE", "PENDING", "COMPLETED"}]
  /\ dispatched \subseteq Signals
  /\ inflight \subseteq (Signals \X Clients)
  /\ connected \in [Clients -> BOOLEAN]
  /\ seen \in [Clients -> SUBSET Signals]
  /\ spoken \in [Clients -> [Signals -> Nat]]
  /\ crashes \in 0..MaxCrashes /\ drops \in 0..MaxDrops

Init ==
  /\ outbox = [s \in Signals |-> "NONE"]
  /\ dispatched = {}
  /\ inflight = {}
  /\ connected = [c \in Clients |-> TRUE]
  /\ seen = [c \in Clients |-> {}]
  /\ spoken = [c \in Clients |-> [s \in Signals |-> 0]]
  /\ crashes = 0 /\ drops = 0

Published == {s \in Signals : outbox[s] # "NONE"}

-----------------------------------------------------------------------------
\* Aggregate + outbox kaydı aynı transaction'da.
Commit(s) ==
  /\ outbox[s] = "NONE"
  /\ outbox' = [outbox EXCEPT ![s] = "PENDING"]
  /\ UNCHANGED <<dispatched, inflight, connected, seen, spoken, crashes, drops>>

\* SSE hub'ı o an bağlı istemcilere fan-out yapar.
Dispatch(s) ==
  /\ outbox[s] = "PENDING"
  /\ s \notin dispatched
  /\ inflight' = inflight \cup {<<s, c>> : c \in {x \in Clients : connected[x]}}
  /\ dispatched' = dispatched \cup {s}
  /\ UNCHANGED <<outbox, connected, seen, spoken, crashes, drops>>

\* Listener başarıyla bitti -> publication COMPLETED.
Complete(s) ==
  /\ outbox[s] = "PENDING"
  /\ s \in dispatched
  /\ outbox' = [outbox EXCEPT ![s] = "COMPLETED"]
  /\ UNCHANGED <<dispatched, inflight, connected, seen, spoken, crashes, drops>>

\* Sunucu çöker: bağlantılar ve bellek kaybolur, outbox kalıcıdır.
Crash ==
  /\ crashes < MaxCrashes
  /\ crashes' = crashes + 1
  /\ dispatched' = {}
  /\ inflight' = {}
  /\ connected' = [c \in Clients |-> FALSE]
  /\ UNCHANGED <<outbox, seen, spoken, drops>>

Disconnect(c) ==
  /\ connected[c] /\ drops < MaxDrops
  /\ connected' = [connected EXCEPT ![c] = FALSE]
  /\ drops' = drops + 1
  /\ UNCHANGED <<outbox, dispatched, inflight, seen, spoken, crashes>>

\* Last-Event-ID ile yeniden bağlanma -> replay (dedup istemcide).
Reconnect(c) ==
  /\ ~connected[c]
  /\ connected' = [connected EXCEPT ![c] = TRUE]
  /\ inflight' = inflight \cup {<<s, c>> : s \in Published}
  /\ UNCHANGED <<outbox, dispatched, seen, spoken, crashes, drops>>

Lose(s, c) ==
  /\ <<s, c>> \in inflight /\ ~connected[c]
  /\ inflight' = inflight \ {<<s, c>>}
  /\ UNCHANGED <<outbox, dispatched, connected, seen, spoken, crashes, drops>>

\* İstemci mesajı alır; daha önce görmediyse sesli okur.
Deliver(s, c) ==
  /\ <<s, c>> \in inflight /\ connected[c]
  /\ inflight' = inflight \ {<<s, c>>}
  /\ IF s \in seen[c]
       THEN UNCHANGED <<seen, spoken>>
       ELSE /\ seen' = [seen EXCEPT ![c] = @ \cup {s}]
            /\ spoken' = [spoken EXCEPT ![c][s] = @ + 1]
  /\ UNCHANGED <<outbox, dispatched, connected, crashes, drops>>

Next ==
  \/ Crash
  \/ \E s \in Signals : Commit(s) \/ Dispatch(s) \/ Complete(s)
  \/ \E c \in Clients : Disconnect(c) \/ Reconnect(c)
  \/ \E s \in Signals, c \in Clients : Lose(s, c) \/ Deliver(s, c)

Fairness ==
  /\ \A s \in Signals : WF_vars(Dispatch(s)) /\ WF_vars(Complete(s))
  /\ \A c \in Clients : WF_vars(Reconnect(c))
  /\ \A s \in Signals, c \in Clients : WF_vars(Deliver(s, c)) /\ WF_vars(Lose(s, c))

Spec == Init /\ [][Next]_vars /\ Fairness

-----------------------------------------------------------------------------
\* INV-ALR-1
SpokenAtMostOnce == \A c \in Clients, s \in Signals : spoken[c][s] <= 1

\* LIV-ALR-1
EventuallyAnnounced ==
  \A s \in Signals, c \in Clients : (outbox[s] # "NONE") ~> (s \in seen[c])

\* Outbox kaydı geri gitmez.
OutboxMonotonic ==
  [][\A s \in Signals :
       /\ outbox[s] = "PENDING"   => outbox'[s] \in {"PENDING", "COMPLETED"}
       /\ outbox[s] = "COMPLETED" => outbox'[s] = "COMPLETED"]_vars
=============================================================================
