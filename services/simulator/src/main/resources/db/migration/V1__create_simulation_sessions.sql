CREATE TABLE simulation_session (
    session_id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'STOPPING')),
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds IN (30, 60, 120)),
    starts_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    completion_reason VARCHAR(64),
    worker_owner UUID,
    lease_until TIMESTAMPTZ,
    fencing_token BIGINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX simulation_one_live_session
    ON simulation_session ((status))
    WHERE status IN ('ACTIVE', 'STOPPING');

CREATE INDEX simulation_session_history
    ON simulation_session (created_at DESC);
