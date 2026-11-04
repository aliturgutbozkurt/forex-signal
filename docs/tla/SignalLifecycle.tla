-------------------------- MODULE SignalLifecycle --------------------------
(***************************************************************************)
(* Signal aggregate yaşam döngüsü (Signal Generation bounded context).    *)
(*                                                                         *)
(* Garanti edilen kurallar (SPEC-001 §6.3, INV-SIG-n):                     *)
(*  INV-SIG-1  Bir (enstrüman, zaman dilimi) anahtarı için aynı anda en   *)
(*             fazla bir ACTIVE sinyal bulunur.                            *)
(*  INV-SIG-2  Aynı anahtarda ardışık iki sinyal arasında en az Cooldown  *)
(*             kapanmış bar vardır (flip-flop / whipsaw koruması).         *)
(*  INV-SIG-3  Terminal durumlar (TP_HIT, SL_HIT, EXPIRED, INVALIDATED)   *)
(*             emicidir; bir daha değişmez.                                *)
(*  INV-SIG-4  Bir sinyal açıldığı bar içinde sonuçlanamaz (repaint yok). *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS Instruments,   \* ör. {EURUSD, XAUUSD}
          Timeframes,    \* ör. {M5, H1}
          MaxBar,        \* model sınırı: kapanmış bar sayısı
          Cooldown,      \* aynı anahtarda iki sinyal arası min bar
          MaxHold,       \* sinyalin EXPIRED olmadan önce açık kalabileceği bar
          MaxSignals     \* model sınırı

ASSUME Cooldown \in Nat \ {0} /\ MaxHold \in Nat \ {0} /\ MaxBar \in Nat

VARIABLES bar,   \* son kapanmış bar indeksi (global saat)
          sig    \* oluşturulmuş tüm sinyallerin sırası (event-sourced log)

vars == <<bar, sig>>

Keys       == Instruments \X Timeframes
Directions == {"BUY", "SELL"}
Terminal   == {"TP_HIT", "SL_HIT", "EXPIRED", "INVALIDATED"}
States     == {"ACTIVE"} \cup Terminal

SignalRec == [key : Keys, dir : Directions, state : States, openedAt : 0..MaxBar]

TypeOK ==
  /\ bar \in 0..MaxBar
  /\ sig \in Seq(SignalRec)
  /\ Len(sig) <= MaxSignals

Opp(d) == IF d = "BUY" THEN "SELL" ELSE "BUY"

HasActive(k) == \E i \in DOMAIN sig : sig[i].key = k /\ sig[i].state = "ACTIVE"

CooldownOK(k) == \A i \in DOMAIN sig : sig[i].key = k => bar - sig[i].openedAt >= Cooldown

NewSig(k, d) == [key |-> k, dir |-> d, state |-> "ACTIVE", openedAt |-> bar]

-----------------------------------------------------------------------------
Init == bar = 0 /\ sig = << >>

\* Yeni bar kapanır (yalnızca kapanmış barlar değerlendirilir).
CloseBar == bar < MaxBar /\ bar' = bar + 1 /\ UNCHANGED sig

\* Confluence skoru eşiği geçti -> sinyal aç (soyut guard).
Open(k, d) ==
  /\ Len(sig) < MaxSignals
  /\ ~HasActive(k)
  /\ CooldownOK(k)
  /\ sig' = Append(sig, NewSig(k, d))
  /\ UNCHANGED bar

\* Aktif sinyale ters yönlü güçlü sinyal: mevcut INVALIDATED, yenisi açılır.
Reverse(i) ==
  /\ Len(sig) < MaxSignals
  /\ sig[i].state = "ACTIVE"
  /\ CooldownOK(sig[i].key)
  /\ sig' = Append([sig EXCEPT ![i].state = "INVALIDATED"],
                   NewSig(sig[i].key, Opp(sig[i].dir)))
  /\ UNCHANGED bar

\* Take-profit / stop-loss tetiklendi (açılış barından sonra).
Resolve(i, outcome) ==
  /\ outcome \in {"TP_HIT", "SL_HIT"}
  /\ sig[i].state = "ACTIVE"
  /\ sig[i].openedAt < bar
  /\ sig' = [sig EXCEPT ![i].state = outcome]
  /\ UNCHANGED bar

Expire(i) ==
  /\ sig[i].state = "ACTIVE"
  /\ bar - sig[i].openedAt >= MaxHold
  /\ sig' = [sig EXCEPT ![i].state = "EXPIRED"]
  /\ UNCHANGED bar

Next ==
  \/ CloseBar
  \/ \E k \in Keys, d \in Directions : Open(k, d)
  \/ \E i \in DOMAIN sig : Reverse(i) \/ Expire(i)
                           \/ \E o \in {"TP_HIT", "SL_HIT"} : Resolve(i, o)

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
\* INV-SIG-1
AtMostOneActivePerKey ==
  \A i, j \in DOMAIN sig :
     (i # j /\ sig[i].key = sig[j].key) =>
        ~(sig[i].state = "ACTIVE" /\ sig[j].state = "ACTIVE")

\* INV-SIG-2
NoFlipFlop ==
  \A i, j \in DOMAIN sig :
     (i < j /\ sig[i].key = sig[j].key) => sig[j].openedAt - sig[i].openedAt >= Cooldown

\* INV-SIG-3 (aksiyon özelliği)
TerminalAbsorbing ==
  [][\A i \in DOMAIN sig : sig[i].state \in Terminal => sig'[i] = sig[i]]_vars

\* Log append-only: geçmiş sinyaller silinmez, yeniden sıralanmaz.
AppendOnly ==
  [][/\ Len(sig') >= Len(sig)
     /\ \A i \in DOMAIN sig : sig'[i].key = sig[i].key /\ sig'[i].dir = sig[i].dir
                              /\ sig'[i].openedAt = sig[i].openedAt]_vars
=============================================================================
