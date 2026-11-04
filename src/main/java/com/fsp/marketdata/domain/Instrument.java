package com.fsp.marketdata.domain;

import com.fsp.shared.AssetClass;
import com.fsp.shared.Currency;
import com.fsp.shared.Symbol;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.ddd.annotation.AggregateRoot;

@AggregateRoot
public final class Instrument {

    private final InstrumentId id;
    private final String displayName;
    private final AssetClass assetClass;
    private final Currency quoteCurrency;
    private final BigDecimal tickSize;
    private final TradingSession session;
    private final boolean delayedFeed;

    public Instrument(
            InstrumentId id,
            String displayName,
            AssetClass assetClass,
            Currency quoteCurrency,
            BigDecimal tickSize,
            TradingSession session,
            boolean delayedFeed
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.assetClass = Objects.requireNonNull(assetClass, "assetClass must not be null");
        this.quoteCurrency = Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        this.tickSize = Objects.requireNonNull(tickSize, "tickSize must not be null");
        if (tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("tickSize must be positive");
        }
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.delayedFeed = delayedFeed;
    }

    public InstrumentId id() {
        return id;
    }

    public Symbol symbol() {
        return id.symbol();
    }

    public String exchange() {
        return id.exchange();
    }

    public String displayName() {
        return displayName;
    }

    public AssetClass assetClass() {
        return assetClass;
    }

    public Currency quoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal tickSize() {
        return tickSize;
    }

    public TradingSession session() {
        return session;
    }

    public boolean delayedFeed() {
        return delayedFeed;
    }

    public boolean matchesQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }
        String q = rawQuery.trim().toUpperCase(Locale.ROOT);
        return id.symbol().value().contains(q)
                || displayName.toUpperCase(Locale.ROOT).contains(q)
                || id.exchange().contains(q);
    }
}

