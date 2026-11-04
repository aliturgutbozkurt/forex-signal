package com.fsp.web;

import com.fsp.ai.api.AiInsightsFacade;
import com.fsp.ai.api.NaturalLanguageAnswerDto;
import com.fsp.alerting.api.AlertingFacade;
import com.fsp.identity.api.IdentityFacade;
import com.fsp.marketdata.api.BarClosed;
import com.fsp.marketdata.api.InstrumentDto;
import com.fsp.marketdata.api.MarketDataFacade;
import com.fsp.performance.api.BacktestRunResultDto;
import com.fsp.performance.api.CostModel;
import com.fsp.performance.api.PerformanceFacade;
import com.fsp.performance.api.PerformanceMetricsDto;
import com.fsp.shared.AssetClass;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.signal.api.SignalDto;
import com.fsp.signal.api.SignalFacade;
import com.fsp.signal.api.StrategySummaryDto;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@NullMarked
@RestController
@RequestMapping("/api")
public class ApiEndpointsController {

    private final MarketDataFacade marketDataFacade;
    private final SignalFacade signalFacade;
    private final AlertingFacade alertingFacade;
    private final PerformanceFacade performanceFacade;
    private final AiInsightsFacade aiInsightsFacade;
    private final IdentityFacade identityFacade;

    public ApiEndpointsController(
            MarketDataFacade marketDataFacade,
            SignalFacade signalFacade,
            AlertingFacade alertingFacade,
            PerformanceFacade performanceFacade,
            AiInsightsFacade aiInsightsFacade,
            IdentityFacade identityFacade
    ) {
        this.marketDataFacade = marketDataFacade;
        this.signalFacade = signalFacade;
        this.alertingFacade = alertingFacade;
        this.performanceFacade = performanceFacade;
        this.aiInsightsFacade = aiInsightsFacade;
        this.identityFacade = identityFacade;
    }

    @GetMapping("/instruments")
    public List<InstrumentDto> searchInstruments(
            @RequestParam(name = "q", required = false) @Nullable String query,
            @RequestParam(name = "assetClass", required = false) @Nullable AssetClass assetClass
    ) {
        return marketDataFacade.searchInstruments(query, assetClass);
    }

    @GetMapping("/signals")
    public List<SignalDto> listSignals(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return signalFacade.listRecentSignals(limit);
    }

    @GetMapping("/bars/{symbol}")
    public List<BarClosed> getBars(
            @PathVariable("symbol") String rawSymbol,
            @RequestParam(name = "tf", defaultValue = "M15") String rawTf,
            @RequestParam(name = "limit", defaultValue = "65") int limit
    ) {
        Symbol symbol = Symbol.of(rawSymbol);
        Timeframe tf = Timeframe.valueOf(rawTf.toUpperCase());
        return marketDataFacade.getEvaluatedBars(symbol, tf, limit);
    }

    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts(
            @RequestParam(name = "clientId", required = false) @Nullable String clientId,
            @RequestHeader(name = "Last-Event-ID", required = false) @Nullable String lastEventId
    ) {
        return alertingFacade.subscribe(clientId, lastEventId);
    }

    @GetMapping("/performance")
    public PerformanceMetricsDto getPerformance() {
        return performanceFacade.getOverallPaperTradingMetrics();
    }

    @GetMapping("/backtest/{symbol}")
    public BacktestRunResultDto runBacktest(
            @PathVariable("symbol") String rawSymbol,
            @RequestParam(name = "tf", defaultValue = "M15") String rawTf
    ) {
        Symbol symbol = Symbol.of(rawSymbol);
        Timeframe tf = Timeframe.valueOf(rawTf.toUpperCase());
        List<BarClosed> bars = marketDataFacade.getEvaluatedBars(symbol, tf, 120);
        return performanceFacade.runBacktestAndWalkForward(symbol, tf, bars, CostModel.realisticDefault());
    }

    @GetMapping("/ai/query")
    public NaturalLanguageAnswerDto queryAi(@RequestParam("q") String query) {
        return aiInsightsFacade.answerNaturalLanguageQuery(query);
    }

    @GetMapping("/mcp/tools")
    public List<Map<String, Object>> listMcpTools() {
        return aiInsightsFacade.listMcpTools();
    }

    @PostMapping("/mcp/invoke")
    public Map<String, Object> invokeMcpTool(
            @RequestParam("tool") String toolName,
            @RequestBody Map<String, String> args
    ) {
        return aiInsightsFacade.invokeMcpTool(toolName, args);
    }

    @GetMapping("/strategy")
    public StrategySummaryDto getStrategy() {
        return signalFacade.getActiveStrategy();
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/market-cache")
    public Map<String, Object> getSharedMarketCache() {
        return marketDataFacade.getSharedHotCacheStatus();
    }

    @GetMapping("/auth/passkey/challenge")
    public Map<String, Object> passkeyChallenge(@RequestParam(name = "user", defaultValue = "trader") String user) {
        return identityFacade.createWebAuthnRegistrationChallenge(user);
    }
}

