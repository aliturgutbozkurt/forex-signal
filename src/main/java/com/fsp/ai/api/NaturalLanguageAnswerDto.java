package com.fsp.ai.api;

import java.util.List;

public record NaturalLanguageAnswerDto(
        String question,
        String answerTr,
        List<String> invokedTools,
        List<String> sourceSignalIds
) {}

