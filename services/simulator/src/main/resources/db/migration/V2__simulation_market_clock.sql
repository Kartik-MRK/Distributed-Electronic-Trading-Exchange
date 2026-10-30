-- Keep historical 30-second sessions, but new claims validate the supported durations in code.
ALTER TABLE simulation_session DROP CONSTRAINT simulation_session_status_check;
ALTER TABLE simulation_session ADD CONSTRAINT simulation_session_status_check
    CHECK (status IN ('STARTING','ACTIVE','STOPPING','COMPLETED','FAILED'));
ALTER TABLE simulation_session DROP CONSTRAINT simulation_session_duration_seconds_check;
ALTER TABLE simulation_session ADD CONSTRAINT simulation_session_duration_seconds_check
    CHECK (duration_seconds IN (30,60,120,300,600));
ALTER TABLE simulation_session ADD COLUMN epoch_id UUID;
ALTER TABLE simulation_session ADD COLUMN base_elapsed_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE simulation_session ADD COLUMN progress_ms BIGINT NOT NULL DEFAULT 0;
DROP INDEX simulation_one_live_session;
CREATE UNIQUE INDEX simulation_one_live_session ON simulation_session ((true))
    WHERE status IN ('STARTING','ACTIVE','STOPPING');
CREATE SEQUENCE simulation_fence;

CREATE TABLE market_clock (
    singleton BOOLEAN PRIMARY KEY DEFAULT true CHECK (singleton),
    epoch_id UUID NOT NULL UNIQUE,
    initialized_at TIMESTAMPTZ NOT NULL,
    elapsed_ms BIGINT NOT NULL DEFAULT 0 CHECK (elapsed_ms >= 0),
    session_id UUID REFERENCES simulation_session(session_id),
    maintenance BOOLEAN NOT NULL DEFAULT true
);
-- Explicit operator initialization is required; migrating must not silently start a new epoch.
CREATE TABLE market_resume_price (
    epoch_id UUID NOT NULL,
    instrument VARCHAR(16) NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    sequence_number BIGINT NOT NULL,
    PRIMARY KEY (epoch_id, instrument)
);
