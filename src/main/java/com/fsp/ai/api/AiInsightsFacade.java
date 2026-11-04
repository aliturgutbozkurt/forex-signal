package com.fsp.ai.api;

import com.fsp.signal.api.SignalDto;
import java.util.List;
import java.util.Map;

public interface AiInsightsFacade {

    String generateGroundedExplanation(SignalDto signal);

    NaturalLanguageAnswerDto answerNaturalLanguageQuery(String query);

    List<Map<String, Object>> listMcpTools();

    Map<String, Object> invokeMcpTool(String toolName, Map<String, String> arguments);
}

