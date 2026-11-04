package com.fsp.ai.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Prompt-injection sanitizer and ablation gate for news/KAP sentiment (SPEC-001 §11, ADR-0005, Issue #33).
 */
public final class SentimentGuard {

    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "disregard previous",
            "system prompt",
            "you are now",
            "override direction",
            "<script",
            "javascript:"
    );

    public record SanitizedNews(String cleanHeadline, boolean injectionDetected) {}

    public SanitizedNews sanitizeHeadline(String rawHeadline) {
        Objects.requireNonNull(rawHeadline, "rawHeadline must not be null");
        String lower = rawHeadline.toLowerCase(Locale.ROOT);
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                return new SanitizedNews("[GÜVENLİK FİLTRESİ: Prompt-injection girişimi engellendi]", true);
            }
        }
        String stripped = rawHeadline.replaceAll("[<>`{}]", "").trim();
        return new SanitizedNews(stripped, false);
    }

    /**
     * Ablation gate (ADR-0005, Issue #33): If including sentiment vote lowers Profit Factor, returns weight 0.0.
     */
    public double calibrateSentimentWeight(double baselineProfitFactor, double withSentimentProfitFactor, double candidateWeight) {
        if (withSentimentProfitFactor < baselineProfitFactor) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(0.25, candidateWeight));
    }
}

