ALTER TABLE market_data.trades ADD COLUMN epoch_id UUID;
ALTER TABLE market_data.trades ADD COLUMN simulation_context JSONB;
ALTER TABLE market_data.trades ADD COLUMN market_time TIMESTAMPTZ;
ALTER TABLE market_data.trades ADD COLUMN elapsed_ms BIGINT;
CREATE INDEX trades_market_time ON market_data.trades(epoch_id,instrument,market_time,sequence_number);
CREATE TABLE market_data.candles (
  epoch_id UUID NOT NULL,
  instrument VARCHAR(32) NOT NULL,
  interval_code VARCHAR(8) NOT NULL,
  open_time BIGINT NOT NULL,
  close_time BIGINT NOT NULL,
  open BIGINT NOT NULL, high BIGINT NOT NULL, low BIGINT NOT NULL, close BIGINT NOT NULL,
  volume BIGINT NOT NULL CHECK(volume>=0), trade_count INTEGER NOT NULL,
  first_elapsed BIGINT NOT NULL, first_sequence BIGINT NOT NULL,
  last_elapsed BIGINT NOT NULL, last_sequence BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  PRIMARY KEY(epoch_id,instrument,interval_code,open_time)
);
