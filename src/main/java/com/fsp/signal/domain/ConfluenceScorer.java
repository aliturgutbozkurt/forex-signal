package com.fsp.signal.domain;

import com.fsp.analysis.api.DivergenceType;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.marketdata.api.ScannerIndicatorValues;
import com.fsp.shared.ConfluenceScore;
import com.fsp.shared.Direction;
import com.fsp.shared.MarketRegime;
import com.fsp.shared.Vote;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic multi-indicator, volume, scanner, and multi-timeframe ConfluenceScorer (SPEC-001 §7.4, FR-04).
 * Formula: {@code score = Σ(w_i(regime) · vote_i) / Σ w_i × 100}.
 */
public final class ConfluenceScorer {

    public record ScoringResult(
            ConfluenceScore score,
            List<Vote> votes,
            boolean volumeConfirmed,
            boolean mtfAligned
    ) {}

    public ScoringResult score(
            IndicatorSnapshotDto snap,
            ScannerIndicatorValues scanner,
            @Nullable IndicatorSnapshotDto higherTfSnap,
            Strategy strategy
    ) {
        Objects.requireNonNull(snap, "snap must not be null");
        Objects.requireNonNull(scanner, "scanner must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        MarketRegime regime = snap.regime();
        RegimeWeights rw = strategy.weightsFor(regime);
        List<Vote> votes = new ArrayList<>(12);

        // 1. TREND: EMA Stack (EMA9 vs EMA21 vs EMA50)
        double emaStackStrength = 0.0;
        if (snap.ema9() > snap.ema21() && snap.ema21() > snap.ema50()) {
            emaStackStrength = 0.90;
        } else if (snap.ema9() < snap.ema21() && snap.ema21() < snap.ema50()) {
            emaStackStrength = -0.90;
        } else {
            emaStackStrength = snap.ema9() >= snap.ema21() ? 0.35 : -0.35;
        }
        votes.add(makeVote("EMA_STACK", "TREND", emaStackStrength, rw.trendWeight(),
                emaStackStrength >= 0 ? "EMA 9/21/50 yükseliş diziliminde" : "EMA 9/21/50 düşüş diziliminde"));

        // 2. TREND: Supertrend & Parabolic SAR
        double stStrength = snap.supertrendBullish() ? 0.85 : -0.85;
        votes.add(makeVote("SUPERTREND", "TREND", stStrength, rw.trendWeight() * 0.9,
                snap.supertrendBullish() ? "Supertrend(10,3) pozitif bölgede" : "Supertrend(10,3) negatif bölgede"));

        // 3. TREND: Ichimoku Cloud & Tenkan/Kijun
        double ichiStrength = 0.0;
        double cloudTop = Math.max(snap.ichimokuSpanA(), snap.ichimokuSpanB());
        double cloudBot = Math.min(snap.ichimokuSpanA(), snap.ichimokuSpanB());
        if (snap.close() > cloudTop && snap.ichimokuTenkan() >= snap.ichimokuKijun()) {
            ichiStrength = 0.80;
        } else if (snap.close() < cloudBot && snap.ichimokuTenkan() <= snap.ichimokuKijun()) {
            ichiStrength = -0.80;
        } else {
            ichiStrength = snap.ichimokuTenkan() >= snap.ichimokuKijun() ? 0.25 : -0.25;
        }
        votes.add(makeVote("ICHIMOKU", "TREND", ichiStrength, rw.trendWeight() * 0.8,
                ichiStrength >= 0 ? "Ichimoku Tenkan/Kijun ve Kumo bulutu pozitif" : "Ichimoku bulutu altında baskı"));

        // 4. TREND: ADX / +DI / -DI
        double diDiff = snap.plusDi14() - snap.minusDi14();
        double adxFactor = Math.min(1.0, snap.adx14() / 35.0);
        double adxVote = clamp(Math.signum(diDiff) * adxFactor);
        votes.add(makeVote("ADX_DI", "TREND", adxVote, rw.trendWeight() * 0.85,
                String.format("ADX %.1f (+DI %.1f / -DI %.1f)", snap.adx14(), snap.plusDi14(), snap.minusDi14())));

        // 5. MOMENTUM: RSI(14) + Divergence
        double rsiVote;
        if (regime == MarketRegime.RANGING) {
            // Mean-reverting RSI in ranging regime
            if (snap.rsi14() < 32.0) rsiVote = 0.85;
            else if (snap.rsi14() > 68.0) rsiVote = -0.85;
            else rsiVote = (50.0 - snap.rsi14()) / 50.0;
        } else {
            // Trend momentum RSI
            rsiVote = clamp((snap.rsi14() - 50.0) / 30.0);
        }
        if (snap.rsiDivergence() == DivergenceType.BULLISH) {
            rsiVote = clamp(rsiVote + 0.35);
        } else if (snap.rsiDivergence() == DivergenceType.BEARISH) {
            rsiVote = clamp(rsiVote - 0.35);
        }
        votes.add(makeVote("RSI_14", "MOMENTUM", rsiVote, rw.momentumWeight(),
                String.format("RSI(14) %.1f (%s)", snap.rsi14(), snap.rsiDivergence().name())));

        // 6. MOMENTUM: MACD(12,26,9)
        double macdVote = snap.macdHistogram() >= 0
                ? (snap.macdLine() >= 0 ? 0.85 : 0.45)
                : (snap.macdLine() <= 0 ? -0.85 : -0.45);
        votes.add(makeVote("MACD", "MOMENTUM", macdVote, rw.momentumWeight(),
                snap.macdHistogram() >= 0 ? "MACD histogramı pozitif" : "MACD histogramı negatif"));

        // 7. MOMENTUM: Stochastic & CCI
        double stochVote = clamp((snap.stochK() - snap.stochD()) / 20.0);
        votes.add(makeVote("STOCHASTIC", "MOMENTUM", stochVote, rw.momentumWeight() * 0.7,
                String.format("Stoch %%K %.1f / %%D %.1f", snap.stochK(), snap.stochD())));

        // 8. VOLATILITY: Bollinger & Keltner
        double bbVote = snap.close() >= snap.bbMiddle() ? 0.55 : -0.55;
        votes.add(makeVote("BOLLINGER", "VOLATILITY", bbVote, rw.volatilityWeight(),
                snap.close() >= snap.bbMiddle() ? "Fiyat Bollinger orta bandı üzerinde" : "Fiyat Bollinger orta bandı altında"));

        // 9. VOLUME: VWAP, CMF(20), MFI(14), RVOL
        double vwapVote = snap.close() >= snap.vwap() ? 0.80 : -0.80;
        if (snap.cmf20() > 0.05 && vwapVote > 0) vwapVote = 0.95;
        if (snap.cmf20() < -0.05 && vwapVote < 0) vwapVote = -0.95;
        votes.add(makeVote("VWAP_CMF", "VOLUME", vwapVote, rw.volumeWeight(),
                String.format("VWAP %.4f, CMF %.2f, RVOL %.2fx", snap.vwap(), snap.cmf20(), snap.relativeVolume())));

        // 10. STRUCTURE: Pivot & Volume Profile POC
        double structVote = (snap.close() >= snap.pivots().pivot() && snap.close() >= snap.volumeProfilePoc())
                ? 0.70
                : ((snap.close() < snap.pivots().pivot() && snap.close() < snap.volumeProfilePoc()) ? -0.70 : 0.15);
        votes.add(makeVote("PIVOT_VP", "STRUCTURE", structVote, rw.structureWeight(),
                String.format("Pivot %.4f ve POC %.4f yapısı", snap.pivots().pivot(), snap.volumeProfilePoc())));

        // 11. TRADINGVIEW SCANNER (if available)
        if (scanner.available()) {
            double tvVote = clamp((scanner.recommendAll() + scanner.recommendMa()) * 0.5);
            votes.add(makeVote("TV_RECOMMEND", "SCANNER", tvVote, rw.scannerWeight(),
                    String.format("TradingView Recommend.All %.2f", scanner.recommendAll())));
        }

        // Compute weighted average in [-100, +100]
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (Vote v : votes) {
            weightedSum += v.weight() * v.signedStrength();
            totalWeight += v.weight();
        }
        double rawScore = totalWeight > 1e-9 ? (weightedSum / totalWeight) * 100.0 : 0.0;

        // Volume confirmation check (SPEC-001 §7.4: RVOL >= 1.2)
        boolean volumeConfirmed = snap.relativeVolume() >= strategy.riskPolicy().minRelativeVolume();
        if (!volumeConfirmed) {
            rawScore *= 0.80; // Penalize low relative volume
        }

        // Multi-Timeframe (MTF) confirmation check
        boolean mtfAligned = true;
        if (higherTfSnap != null) {
            boolean higherBullish = higherTfSnap.ema21() >= higherTfSnap.ema50() && higherTfSnap.supertrendBullish();
            boolean higherBearish = higherTfSnap.ema21() < higherTfSnap.ema50() && !higherTfSnap.supertrendBullish();
            if ((rawScore > 0 && higherBearish) || (rawScore < 0 && higherBullish)) {
                mtfAligned = false;
                rawScore *= 0.55; // Penalize MTF disagreement (Issue #21 acceptance criterion)
            }
        }

        double clampedScore = Math.max(-100.0, Math.min(100.0, rawScore));
        return new ScoringResult(new ConfluenceScore(clampedScore), List.copyOf(votes), volumeConfirmed, mtfAligned);
    }

    private static Vote makeVote(String indicator, String category, double signedStrength, double weight, String summaryTr) {
        double clamped = clamp(signedStrength);
        Direction dir = clamped >= 0.0 ? Direction.BUY : Direction.SELL;
        return new Vote(indicator, category, dir, clamped, Math.max(0.0, weight), summaryTr);
    }

    private static double clamp(double val) {
        if (!Double.isFinite(val)) return 0.0;
        return Math.max(-1.0, Math.min(1.0, val));
    }
}

