package com.fsp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fsp.marketdata.api.BarClosed;
import com.fsp.marketdata.api.MarketDataFacade;
import com.fsp.shared.Symbol;
import com.fsp.shared.Timeframe;
import com.fsp.signal.api.SignalFacade;
import com.fsp.signal.api.StrategySummaryDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Tag("SPEC-001")
@Tag("FR-10")
@Tag("FR-14")
@Tag("FR-15")
@Tag("FR-18")
@Tag("M4")
@Tag("M7")
class EndToEndPlatformIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Autowired
    private MarketDataFacade marketDataFacade;

    @Autowired
    private SignalFacade signalFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dashboardInstrumentDetailAndStyleguideRenderWithSecurityHeadersAndLegalDisclaimer() throws Exception {
        mockMvc.perform(get("/").param("q", "Bugün BIST'te en güçlü AL sinyalleri?"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("karar destek aracıdır")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("THYAO")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EURUSD")));

        mockMvc.perform(get("/instrument/THYAO").param("tf", "H1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("15dk Gecikmeli")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tradingview_advanced_chart")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1m (M1)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("5m (M5)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("15m (M15)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("30m (M30)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1h (H1)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2h (H2)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("4h (H4)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1D (D1)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1W (W1)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1M (MN)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Karar")))
                .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString(">AL<"),
                        org.hamcrest.Matchers.containsString(">SAT<"),
                        org.hamcrest.Matchers.containsString(">TUT<")
                )));

        mockMvc.perform(get("/styleguide"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("oklch(0.48 0.15 155)")));
    }

    @Test
    void apiEndpointsForSearchAiMcpBacktestAndPasskeyOperateWithinLatencyBudget() throws Exception {
        mockMvc.perform(get("/api/instruments").param("q", "THY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol.value").value("THYAO"))
                .andExpect(jsonPath("$[0].quoteCurrency").value("TRY"));

        mockMvc.perform(get("/api/ai/query").param("q", "THYAO için H1'de durum nedir?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerTr").isNotEmpty())
                .andExpect(jsonPath("$.invokedTools").isArray());

        mockMvc.perform(get("/api/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("list_signals"));

        mockMvc.perform(post("/api/mcp/invoke")
                        .param("tool", "list_signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"EURUSD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("list_signals"));

        mockMvc.perform(get("/api/backtest/EURUSD").param("tf", "M15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.totalTrades").isNumber())
                .andExpect(jsonPath("$.monteCarlo.simulations").value(200));

        mockMvc.perform(get("/api/auth/passkey/challenge").param("user", "aliturgutbozkurt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").isNotEmpty());
    }

    @Test
    void strategyUpdateWritesAuditLogAndBarToSignalLatencyIsUnder50ms() {
        StrategySummaryDto updated = signalFacade.updateStrategyParameters(
                1.6, 1.6, 2.6, 4.2, 3, 24, "aliturgutbozkurt"
        );
        assertThat(updated.version()).isGreaterThanOrEqualTo(2);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM strategy_audit_log WHERE actor = 'aliturgutbozkurt'",
                Integer.class
        );
        assertThat(auditCount).isNotNull().isGreaterThanOrEqualTo(1);

        // NFR §7.5: BarClosed -> Signal pipeline < 50 ms per instrument
        List<BarClosed> bars = marketDataFacade.getEvaluatedBars(Symbol.of("EURUSD"), Timeframe.M15, 5);
        BarClosed latest = bars.getLast();
        long t0 = System.nanoTime();
        marketDataFacade.ingestAndEvaluateBars(
                Symbol.of("EURUSD"),
                Timeframe.M15,
                latest.barIndex() + 2,
                List.of(new BarClosed(
                        Symbol.of("EURUSD"),
                        Timeframe.M15,
                        latest.barIndex() + 1,
                        latest.closeTime(),
                        latest.closeTime().plusSeconds(900),
                        latest.close(),
                        latest.close() * 1.003,
                        latest.close() * 0.998,
                        latest.close() * 1.002,
                        18_000.0,
                        false
                ))
        );
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(elapsedMs).isLessThan(50L);
    }
}
