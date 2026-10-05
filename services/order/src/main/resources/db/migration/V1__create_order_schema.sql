-- ============================================================
-- DETE Order Service Schema Migration V1
-- ============================================================

CREATE SCHEMA IF NOT EXISTS order_svc;

CREATE TABLE order_svc.orders (
    order_id        UUID PRIMARY KEY,
    account_id      UUID NOT NULL,
    instrument      VARCHAR(16) NOT NULL,
    side            VARCHAR(4) NOT NULL,
    order_type      VARCHAR(8) NOT NULL,
    price           BIGINT,
    original_qty    BIGINT NOT NULL,
    remaining_qty   BIGINT NOT NULL,
    filled_qty      BIGINT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL,
    idempotency_key UUID NOT NULL UNIQUE,
    reject_reason   VARCHAR(256),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_account_status ON order_svc.orders (account_id, status);
CREATE INDEX idx_orders_created_at ON order_svc.orders (created_at DESC);

CREATE TABLE order_svc.outbox (
    outbox_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic      VARCHAR(128) NOT NULL,
    key        VARCHAR(128),
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published  BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_order_outbox_unpublished ON order_svc.outbox (created_at) WHERE published = false;
