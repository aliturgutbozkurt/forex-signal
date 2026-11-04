---------------------------- MODULE BarIngestion ----------------------------
(***************************************************************************)
(* Market Data -> Technical Analysis hattı: "no-repaint" garantisi.        *)
(*                                                                         *)
(* Kaynak (TradingView scanner / OHLCV adapter) barları sırasız, eksik ve  *)
(* tekrarlı getirebilir. Değerlendirici (evaluator) şu kuralları korur:    *)
(*  INV-BAR-1  Yalnızca KAPANMIŞ barlar değerlendirilir (oluşan bar asla).*)
(*  INV-BAR-2  Her bar en fazla bir kez, kesin artan sırada ve boşluksuz  *)
(*             değerlendirilir (watermark).                                *)
(*  LIV-BAR-1  Kapanan her bar sonunda değerlendirilir (backfill sayesinde)*)
(***************************************************************************)
EXTENDS Naturals, Sequences

CONSTANTS MaxT   \* model sınırı: zaman adımı = bar

VARIABLES now,       \* şu an oluşan barın indeksi; < now olanlar kapanmış
          received,  \* adapter'dan alınmış kapanmış bar indeksleri
          watermark, \* sıradaki değerlendirilecek bar
          evalLog    \* değerlendirilen barlar (sıra)

vars == <<now, received, watermark, evalLog>>

Closed == 0..(now - 1)

TypeOK ==
  /\ now \in 0..MaxT
  /\ received \subseteq 0..MaxT
  /\ watermark \in 0..MaxT
  /\ evalLog \in Seq(0..MaxT)

Init == now = 0 /\ received = {} /\ watermark = 0 /\ evalLog = << >>

Advance == now < MaxT /\ now' = now + 1 /\ UNCHANGED <<received, watermark, evalLog>>

\* Poll: kapanmış barların keyfi bir alt kümesi gelir (sırasız/tekrarlı/eksik).
Poll == \E S \in (SUBSET Closed) \ {{}} :
          /\ received' = received \cup S
          /\ UNCHANGED <<now, watermark, evalLog>>

\* Boşluk tespit edildi -> REST ile backfill.
Backfill ==
  /\ watermark < now
  /\ watermark \notin received
  /\ received' = received \cup {watermark}
  /\ UNCHANGED <<now, watermark, evalLog>>

Evaluate ==
  /\ watermark \in received
  /\ watermark < now
  /\ evalLog' = Append(evalLog, watermark)
  /\ watermark' = watermark + 1
  /\ UNCHANGED <<now, received>>

Next == Advance \/ Poll \/ Backfill \/ Evaluate

Spec == Init /\ [][Next]_vars
        /\ WF_vars(Advance) /\ WF_vars(Backfill) /\ WF_vars(Evaluate)

-----------------------------------------------------------------------------
\* INV-BAR-1
OnlyClosedBars == \A i \in DOMAIN evalLog : evalLog[i] < now

\* INV-BAR-2
StrictlyOrderedNoGaps == \A i \in DOMAIN evalLog : evalLog[i] = i - 1

\* LIV-BAR-1
EveryClosedBarEvaluated == \A b \in 0..(MaxT - 1) : (b < now) ~> (watermark > b)
=============================================================================
