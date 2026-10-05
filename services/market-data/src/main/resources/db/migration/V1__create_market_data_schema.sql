-- Phase 6: Market Data Service — Historical Trade Storage
-- Schema and table for storing all executed trades (read model only for Phase 12 Replay Viewer)

CREATE SCHEMA IF NOT EXISTS market_data;

CREATE TABLE IF NOT EXISTS market_data.trades (
    trade_id UUID PRIMARY KEY,
    instrument VARCHAR(32) NOT NULL,
    sequence_number BIGINT NOT NULL,
    price BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    buy_order_id UUID NOT NULL,
    sell_order_id UUID NOT NULL,
    buy_account_id UUID NOT NULL,
    sell_account_id UUID NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trades_instrument_executed_at
    ON market_data.trades (instrument, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_trades_instrument_seq
    ON market_data.trades (instrument, sequence_number DESC);
