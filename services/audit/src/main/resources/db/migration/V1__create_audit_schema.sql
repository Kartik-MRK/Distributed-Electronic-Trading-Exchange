-- Phase 7: Audit Service — Immutable Append-Only Compliance Log

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS audit.audit_log (
    entry_id     BIGSERIAL PRIMARY KEY,
    event_id     UUID NOT NULL UNIQUE,
    event_type   VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id   UUID NOT NULL,
    actor_id     UUID,
    instrument   VARCHAR(16),
    payload      JSONB NOT NULL,
    trace_id     VARCHAR(64),
    event_time   TIMESTAMPTZ NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_subject ON audit.audit_log(subject_id, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_event_type ON audit.audit_log(event_type, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_instrument ON audit.audit_log(instrument, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_trace_id ON audit.audit_log(trace_id, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_event_time ON audit.audit_log(event_time DESC);

-- Enforce immutability of audit log records: NO UPDATE OR DELETE ALLOWED
CREATE OR REPLACE FUNCTION audit.prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit.audit_log is append-only and immutable. Modifications or deletions are strictly prohibited for compliance.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_immutable ON audit.audit_log;
CREATE TRIGGER trg_audit_immutable
BEFORE UPDATE OR DELETE ON audit.audit_log
FOR EACH ROW
EXECUTE FUNCTION audit.prevent_audit_modification();
