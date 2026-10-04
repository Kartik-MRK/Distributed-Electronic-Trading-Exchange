-- DETE Account & Ledger Service: Initial Schema
CREATE SCHEMA IF NOT EXISTS account;

CREATE TABLE IF NOT EXISTS account.accounts (
    account_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS account.balances (
    account_id UUID NOT NULL REFERENCES account.accounts(account_id) ON DELETE CASCADE,
    asset      VARCHAR(16) NOT NULL,
    available  BIGINT NOT NULL DEFAULT 0 CHECK (available >= 0),
    reserved   BIGINT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    PRIMARY KEY (account_id, asset),
    CONSTRAINT total_non_negative CHECK (available + reserved >= 0)
);

CREATE TABLE IF NOT EXISTS account.ledger_entries (
    entry_id     BIGSERIAL PRIMARY KEY,
    account_id   UUID NOT NULL REFERENCES account.accounts(account_id) ON DELETE CASCADE,
    asset        VARCHAR(16) NOT NULL,
    entry_type   VARCHAR(32) NOT NULL,   -- DEPOSIT, RESERVE, RELEASE, DEBIT, CREDIT, FEE
    amount       BIGINT NOT NULL,
    reference_id UUID NOT NULL,
    sequence_num BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ledger_idempotency ON account.ledger_entries(account_id, reference_id, entry_type);
CREATE INDEX IF NOT EXISTS idx_ledger_account ON account.ledger_entries(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS account.settlements (
    settlement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id      UUID NOT NULL UNIQUE,
    buyer_id      UUID NOT NULL,
    seller_id     UUID NOT NULL,
    instrument    VARCHAR(16) NOT NULL,
    price         BIGINT NOT NULL,
    quantity      BIGINT NOT NULL,
    settled_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_settlements_buyer ON account.settlements(buyer_id, settled_at DESC);
CREATE INDEX IF NOT EXISTS idx_settlements_seller ON account.settlements(seller_id, settled_at DESC);

CREATE TABLE IF NOT EXISTS account.outbox (
    outbox_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic      VARCHAR(128) NOT NULL,
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published  BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON account.outbox(created_at ASC) WHERE NOT published;

-- Enforce immutability of ledger entries: NO UPDATE OR DELETE ALLOWED
CREATE OR REPLACE FUNCTION account.prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'account.ledger_entries is append-only and immutable. Modifications or deletions are prohibited.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ledger_immutable ON account.ledger_entries;
CREATE TRIGGER trg_ledger_immutable
BEFORE UPDATE OR DELETE ON account.ledger_entries
FOR EACH ROW
EXECUTE FUNCTION account.prevent_ledger_modification();
