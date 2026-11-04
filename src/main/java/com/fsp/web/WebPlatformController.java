package com.fsp.web;

import com.fsp.ai.api.AiInsightsFacade;
import com.fsp.analysis.api.AnalysisFacade;
import com.fsp.analysis.api.IndicatorSnapshotDto;
import com.fsp.marketdata.api.BarClosed;
import com.fsp.marketdata.api.InstrumentDto;
import com.fsp.marketdata.api.MarketDataFacade;
import com.fsp.performance.api.PerformanceFacade;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.signal.api.SignalFacade;
import com.fsp.watchlist.api.WatchlistFacade;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@NullMarked
@Controller
public class WebPlatformController {

    private final MarketDataFacade marketDataFacade;
    private final AnalysisFacade analysisFacade;
    private final SignalFacade signalFacade;
    private final PerformanceFacade performanceFacade;
    private final WatchlistFacade watchlistFacade;
    private final AiInsightsFacade aiInsightsFacade;

    public WebPlatformController(
            MarketDataFacade marketDataFacade,
            AnalysisFacade analysisFacade,
            SignalFacade signalFacade,
            PerformanceFacade performanceFacade,
            WatchlistFacade watchlistFacade,
            AiInsightsFacade aiInsightsFacade
    ) {
        this.marketDataFacade = marketDataFacade;
        this.analysisFacade = analysisFacade;
        this.signalFacade = signalFacade;
        this.performanceFacade = performanceFacade;
        this.watchlistFacade = watchlistFacade;
        this.aiInsightsFacade = aiInsightsFacade;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmupInitialPipeline() {
        marketDataFacade.refreshLiveQuotes();
        List<Symbol> coreSymbols = List.of(
                Symbol.of("EURUSD"),
                Symbol.of("THYAO"),
                Symbol.of("XAUUSD"),
                Symbol.of("AAPL"),
                Symbol.of("NVDA")
        );
        for (Symbol sym : coreSymbols) {
            marketDataFacade.getEvaluatedBars(sym, Timeframe.M15, 65);
            marketDataFacade.getEvaluatedBars(sym, Timeframe.H1, 65);
        }
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(name = "q", required = false) @Nullable String query, Model model) {
        model.addAttribute("degradedMode", marketDataFacade.isDegradedMode());
        model.addAttribute("strategy", signalFacade.getActiveStrategy());
        model.addAttribute("metrics", performanceFacade.getOverallPaperTradingMetrics());
        model.addAttribute("signals", signalFacade.listRecentSignals(12));
        model.addAttribute("instruments", marketDataFacade.allInstruments());
        model.addAttribute("subscriptions", watchlistFacade.getSubscriptions());
        model.addAttribute("voicePref", watchlistFacade.getVoicePreference());
        model.addAttribute("aiQuery", query != null ? query : "");
        if (query != null && !query.isBlank()) {
            model.addAttribute("aiAnswer", aiInsightsFacade.answerNaturalLanguageQuery(query));
        }
        return "dashboard";
    }

    @GetMapping("/instrument/{symbol}")
    public String instrumentDetail(
            @PathVariable("symbol") String rawSymbol,
            @RequestParam(name = "tf", defaultValue = "M15") String rawTf,
            Model model
    ) {
        Symbol symbol = Symbol.of(rawSymbol);
        Timeframe tf = Timeframe.valueOf(rawTf.toUpperCase());
        InstrumentDto instrument = marketDataFacade.findInstrument(symbol)
                .orElseGet(() -> marketDataFacade.allInstruments().getFirst());
        List<BarClosed> bars = marketDataFacade.getEvaluatedBars(instrument.symbol(), tf, 65);
        IndicatorSnapshotDto snapshot = analysisFacade.getLatestSnapshot(instrument.symbol(), tf)
                .orElseGet(() -> {
                    IndicatorSnapshotDto last = null;
                    for (BarClosed b : bars) {
                        last = analysisFacade.onBarClosed(b);
                    }
                    return last != null ? last : analysisFacade.onBarClosed(bars.getLast());
                });

        model.addAttribute("instrument", instrument);
        model.addAttribute("timeframe", tf);
        model.addAttribute("bars", bars);
        model.addAttribute("snapshot", snapshot);
        model.addAttribute("signals", signalFacade.listSignalsForSymbol(instrument.symbol()));
        return "instrument-detail";
    }

    @GetMapping("/styleguide")
    public String styleguide() {
        return "styleguide";
    }
}

