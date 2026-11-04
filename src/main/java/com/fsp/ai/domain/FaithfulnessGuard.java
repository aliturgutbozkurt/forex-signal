package com.fsp.ai.domain;

import com.fsp.shared.Vote;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Faithfulness & hallucination guardrail for AI signal explanations (SPEC-001 FR-09, ADR-0005, Issue #32).
 * Verifies that explanations are <= 3 sentences and only reference indicators present in the Signal's Rationale.
 */
public final class FaithfulnessGuard {

    private static final List<String> KNOWN_INDICATOR_TOKENS = List.of(
            "EMA", "SUPERTREND", "ICHIMOKU", "ADX", "RSI", "MACD",
            "STOCH", "BOLLINGER", "VWAP", "CMF", "PIVOT", "POC", "RECOMMEND", "ELLIOTT", "FIBONACCI_ARC", "GANN"
    );

    public double evaluateFaithfulness(String explanation, List<Vote> rationale) {
        if (explanation == null || explanation.isBlank()) {
            return 0.0;
        }
        Set<String> allowedTokens = rationale.stream()
                .map(v -> v.indicator().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        String upper = explanation.toUpperCase(Locale.ROOT);
        int referencedCount = 0;
        int groundedCount = 0;

        for (String token : KNOWN_INDICATOR_TOKENS) {
            if (upper.contains(token)) {
                referencedCount++;
                boolean inRationale = allowedTokens.stream()
                        .anyMatch(allowed -> allowed.contains(token) || token.contains(allowed));
                if (inRationale) {
                    groundedCount++;
                }
            }
        }
        if (referencedCount == 0) {
            return 1.0;
        }
        return (double) groundedCount / referencedCount;
    }

    public int countSentences(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return 0;
        }
        String[] parts = explanation.trim().split("[.!?]+\\s*");
        int count = 0;
        for (String p : parts) {
            if (!p.isBlank()) {
                count++;
            }
        }
        return count;
    }

    public boolean isCompliant(String explanation, List<Vote> rationale) {
        int sentences = countSentences(explanation);
        return sentences >= 1 && sentences <= 3 && evaluateFaithfulness(explanation, rationale) >= 0.90;
    }
}

