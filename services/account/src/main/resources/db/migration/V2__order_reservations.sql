CREATE TABLE account.order_reservations (
    order_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account.accounts(account_id),
    asset VARCHAR(16) NOT NULL,
    original_amount BIGINT NOT NULL CHECK (original_amount > 0),
    remaining_amount BIGINT NOT NULL CHECK (remaining_amount >= 0),
    spent_amount BIGINT NOT NULL DEFAULT 0 CHECK (spent_amount >= 0),
    released_amount BIGINT NOT NULL DEFAULT 0 CHECK (released_amount >= 0),
    settled_qty BIGINT NOT NULL DEFAULT 0 CHECK (settled_qty >= 0),
    closed BOOLEAN NOT NULL DEFAULT false,
    CHECK (original_amount = remaining_amount + spent_amount + released_amount)
);
CREATE INDEX reservation_open ON account.order_reservations(account_id) WHERE NOT closed;
