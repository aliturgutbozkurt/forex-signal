package com.fsp.ai.application;

import com.fsp.ai.api.AiInsightsFacade;
import com.fsp.ai.api.NaturalLanguageAnswerDto;
import com.fsp.ai.api.SignalExplained;
import com.fsp.ai.domain.FaithfulnessGuard;
import com.fsp.ai.domain.SentimentGuard;
import com.fsp.marketdata.api.InstrumentDto;
import com.fsp.marketdata.api.MarketDataFacade;
import com.fsp.performance.api.PerformanceFacade;
import com.fsp.performance.api.PerformanceMetricsDto;
import com.fsp.shared.AssetClass;
import com.fsp.shared.Direction;
import com.fsp.shared.Symbol;
import com.fsp.shared.Vote;
import com.fsp.signal.api.SignalDto;
import com.fsp.signal.api.SignalFacade;
import com.fsp.signal.api.SignalOpened;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class AiInsightsApplicationService implements AiInsightsFacade {

    private final SignalFacade signalFacade;
    private final MarketDataFacade marketDataFacade;
    private final PerformanceFacade performanceFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final FaithfulnessGuard faithfulnessGuard = new FaithfulnessGuard();
    private final SentimentGuard sentimentGuard = new SentimentGuard();

    public AiInsightsApplicationService(
            SignalFacade signalFacade,
            MarketDataFacade marketDataFacade,
            PerformanceFacade performanceFacade,
            ApplicationEventPublisher eventPublisher
    ) {
        this.signalFacade = signalFacade;
        this.marketDataFacade = marketDataFacade;
        this.performanceFacade = performanceFacade;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onSignalOpened(SignalOpened event) {
        SignalDto signal = event.signal();
        String explanation = generateGroundedExplanation(signal);
        double score = faithfulnessGuard.evaluateFaithfulness(explanation, signal.rationale());
        if (score >= 0.90) {
            signalFacade.attachExplanation(signal.signalId(), explanation);
            eventPublisher.publishEvent(new SignalExplained(signal.signalId(), explanation, score, Instant.now()));
        }
    }

    @Override
    public String generateGroundedExplanation(SignalDto signal) {
        List<Vote> topVotes = signal.rationale().stream()
                .sorted(Comparator.comparingDouble((Vote v) -> Math.abs(v.signedStrength() * v.weight())).reversed())
                .limit(3)
                .toList();

        String voteDescriptions = topVotes.stream()
                .map(v -> v.indicator() + " (" + v.summaryTr() + ")")
                .collect(Collectors.joining(", "));

        String sentence1 = String.format(Locale.forLanguageTag("tr-TR"),
                "%s %s zaman diliminde %s rejimi altında %+.1f confluence skoru ile %s sinyali üretti.",
                signal.symbol().value(),
                signal.timeframe().label(),
                signal.regime().labelTr(),
                signal.confluenceScore(),
                signal.direction().labelTr());

        String sentence2 = topVotes.isEmpty()
                ? "Temel göstergeler yön uyumunu teyit etmektedir."
                : "Sinyalin ana dayanakları: " + voteDescriptions + ".";

        String sentence3 = String.format(Locale.forLanguageTag("tr-TR"),
                "Risk/Ödül oranı %.2fR olup stop seviyesi %s ve ilk hedef (TP1) %s olarak hesaplanmıştır.",
                signal.riskRewardTp1(),
                signal.stopLoss().amount().toPlainString(),
                signal.tp1().amount().toPlainString());

        return sentence1 + " " + sentence2 + " " + sentence3;
    }

    @Override
    public NaturalLanguageAnswerDto answerNaturalLanguageQuery(String query) {
        String cleanQuery = sentimentGuard.sanitizeHeadline(query).cleanHeadline();
        String upper = cleanQuery.toUpperCase(Locale.ROOT);
        List<String> invokedTools = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        // Tool 1: Check if user asks about a specific symbol
        for (InstrumentDto inst : marketDataFacade.allInstruments()) {
            if (upper.contains(inst.symbol().value())) {
                invokedTools.add("list_signals(symbol=" + inst.symbol().value() + ")");
                List<SignalDto> symSignals = signalFacade.listSignalsForSymbol(inst.symbol());
                if (!symSignals.isEmpty()) {
                    SignalDto latest = symSignals.getFirst();
                    invokedTools.add("explain_signal(signalId=" + latest.signalId() + ")");
                    sources.add(latest.signalId());
                    String exp = latest.explanation() != null
                            ? latest.explanation()
                            : generateGroundedExplanation(latest);
                    return new NaturalLanguageAnswerDto(
                            query,
                            exp + " [Kaynak: " + latest.signalId() + "]",
                            invokedTools,
                            sources
                    );
                }
            }
        }

        // Tool 2: Commodity / Forex / AL / SAT filter query
        invokedTools.add("list_signals(limit=20)");
        List<SignalDto> recent = signalFacade.listRecentSignals(20);
        boolean filterCommodity = upper.contains("EMTIA") || upper.contains("EMTİA") || upper.contains("ALTIN") || upper.contains("PETROL");
        Direction dirFilter = upper.contains("SAT") && !upper.contains("AL") ? Direction.SELL
                : (upper.contains("AL") ? Direction.BUY : null);

        List<SignalDto> matched = recent.stream()
                .filter(s -> {
                    if (dirFilter != null && s.direction() != dirFilter) return false;
                    if (filterCommodity) {
                        return marketDataFacade.findInstrument(s.symbol())
                                .map(i -> i.assetClass() == AssetClass.COMMODITY)
                                .orElse(false);
                    }
                    return true;
                })
                .sorted(Comparator.comparingInt(SignalDto::confidence).reversed())
                .limit(3)
                .toList();

        if (!matched.isEmpty()) {
            for (SignalDto m : matched) {
                sources.add(m.signalId());
            }
            String summary = matched.stream()
                    .map(s -> s.symbol().value() + " (" + s.timeframe().label() + " " + s.direction().labelTr()
                            + ", Güven: %" + s.confidence() + ", ID: " + s.signalId() + ")")
                    .collect(Collectors.joining("; "));
            return new NaturalLanguageAnswerDto(
                    query,
                    "Read-model sorgu sonucuna göre en güçlü sinyaller: " + summary + ".",
                    invokedTools,
                    sources
            );
        }

        invokedTools.add("get_performance_metrics()");
        PerformanceMetricsDto perf = performanceFacade.getOverallPaperTradingMetrics();
        return new NaturalLanguageAnswerDto(
                query,
                String.format(Locale.forLanguageTag("tr-TR"),
                        "Şu anda kriterlere uygun aktif sinyal bulunmadı. Sistem genel win-rate oranı %%%.1f, profit factor %.2f (%s).",
                        perf.winRatePct(), perf.profitFactor(), perf.qualityBadge()),
                invokedTools,
                sources
        );
    }

    @Override
    public List<Map<String, Object>> listMcpTools() {
        return List.of(
                Map.of(
                        "name", "list_signals",
                        "description", "Lists recent BUY/SELL signals with confidence, SL/TP, and rationale",
                        "inputSchema", Map.of("type", "object", "properties", Map.of("symbol", Map.of("type", "string")))
                ),
                Map.of(
                        "name", "explain_signal",
                        "description", "Returns grounded Turkish XAI explanation for a given signalId",
                        "inputSchema", Map.of("type", "object", "properties", Map.of("signalId", Map.of("type", "string")))
                ),
                Map.of(
                        "name", "get_performance_metrics",
                        "description", "Returns walk-forward and paper-trading performance metrics",
                        "inputSchema", Map.of("type", "object", "properties", Map.of())
                )
        );
    }

    @Override
    public Map<String, Object> invokeMcpTool(String toolName, Map<String, String> arguments) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tool", toolName);
        switch (toolName) {
            case "list_signals" -> {
                String sym = arguments.get("symbol");
                List<SignalDto> signals = (sym != null && !sym.isBlank())
                        ? signalFacade.listSignalsForSymbol(Symbol.of(sym))
                        : signalFacade.listRecentSignals(10);
                response.put("signals", signals);
            }
            case "explain_signal" -> {
                String id = arguments.getOrDefault("signalId", "");
                Optional<SignalDto> sig = signalFacade.findSignalById(id);
                response.put("signalId", id);
                response.put("explanation", sig.map(this::generateGroundedExplanation).orElse("Signal not found"));
            }
            case "get_performance_metrics" -> response.put("metrics", performanceFacade.getOverallPaperTradingMetrics());
            default -> throw new IllegalArgumentException("Unknown MCP tool: " + toolName);
        }
        return response;
    }
}

