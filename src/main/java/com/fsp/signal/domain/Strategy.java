package com.fsp.signal.domain;

import com.fsp.shared.MarketRegime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.ddd.annotation.AggregateRoot;

/**
 * Versioned Strategy aggregate root (SPEC-001 §6.1, FR-16, Issue #20).
 * Every configuration change produces a new immutable version while preserving historical version references.
 */
@AggregateRoot
public final class Strategy {

    private final String strategyId;
    private final String name;
    private final int version;
    private final Map<MarketRegime, RegimeWeights> regimeWeights;
    private final RiskPolicy riskPolicy;

    public Strategy(
            String strategyId,
            String name,
            int version,
            Map<MarketRegime, RegimeWeights> regimeWeights,
            RiskPolicy riskPolicy
    ) {
        this.strategyId = Objects.requireNonNull(strategyId, "strategyId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("Strategy version must be >= 1");
        }
        this.version = version;
        Objects.requireNonNull(regimeWeights, "regimeWeights must not be null");
        this.regimeWeights = new EnumMap<>(regimeWeights);
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy must not be null");
    }

    public static Strategy defaultMultiAssetConfluence() {
        Map<MarketRegime, RegimeWeights> weights = new EnumMap<>(MarketRegime.class);
        for (MarketRegime r : MarketRegime.values()) {
            weights.put(r, RegimeWeights.forRegime(r));
        }
        return new Strategy(
                "FSP-CONFLUENCE-CORE",
                "FSP Multi-Regime Confluence v1",
                1,
                weights,
                RiskPolicy.standard()
        );
    }

    public Strategy withUpdatedConfiguration(
            Map<MarketRegime, RegimeWeights> updatedWeights,
            RiskPolicy updatedRiskPolicy
    ) {
        return new Strategy(
                this.strategyId,
                this.name,
                this.version + 1,
                updatedWeights,
                updatedRiskPolicy
        );
    }

    public String strategyId() {
        return strategyId;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public RegimeWeights weightsFor(MarketRegime regime) {
        return regimeWeights.getOrDefault(regime, RegimeWeights.forRegime(regime));
    }

    public Map<MarketRegime, RegimeWeights> allRegimeWeights() {
        return Map.copyOf(regimeWeights);
    }

    public RiskPolicy riskPolicy() {
        return riskPolicy;
    }
}

