-- Flyway V1: PostgreSQL 18 + TimescaleDB hypertable schema (SPEC-001 §7.1)
CREATE TABLE IF NOT EXISTS instruments (
    symbol VARCHAR(16) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    exchange VARCHAR(32) NOT NULL,
    asset_class VARCHAR(32) NOT NULL,
    quote_currency VARCHAR(8) NOT NULL,
    tick_size NUMERIC(18, 8) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    delayed_feed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS bars (
    symbol VARCHAR(16) NOT NULL,
    timeframe VARCHAR(8) NOT NULL,
    bar_index BIGINT NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(18, 8) NOT NULL,
    high_price NUMERIC(18, 8) NOT NULL,
    low_price NUMERIC(18, 8) NOT NULL,
    close_price NUMERIC(18, 8) NOT NULL,
    volume NUMERIC(24, 4) NOT NULL,
    PRIMARY KEY (symbol, timeframe, open_time, bar_index)
);

-- TimescaleDB hypertable conversion when extension is installed:
-- SELECT create_hypertable('bars', by_range('open_time'), if_not_exists => TRUE);

CREATE TABLE IF NOT EXISTS signals (
    signal_id VARCHAR(64) PRIMARY KEY,
    symbol VARCHAR(16) NOT NULL,
    timeframe VARCHAR(8) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    state VARCHAR(24) NOT NULL,
    entry_price NUMERIC(18, 8) NOT NULL,
    stop_loss NUMERIC(18, 8) NOT NULL,
    tp1_price NUMERIC(18, 8) NOT NULL,
    tp2_price NUMERIC(18, 8) NOT NULL,
    tp3_price NUMERIC(18, 8) NOT NULL,
    confidence INT NOT NULL,
    confluence_score DOUBLE PRECISION NOT NULL,
    regime VARCHAR(32) NOT NULL,
    strategy_id VARCHAR(64) NOT NULL,
    strategy_version INT NOT NULL,
    opened_at_bar BIGINT NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    explanation TEXT
);

CREATE TABLE IF NOT EXISTS event_publication (
    id UUID NOT NULL PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ
);

