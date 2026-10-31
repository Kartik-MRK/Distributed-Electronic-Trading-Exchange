CREATE TABLE matching_admission (
    epoch_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    partition_id INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    command_event_id UUID,
    session_id UUID,
    fencing_token BIGINT NOT NULL,
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(128) NOT NULL,
    context JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (epoch_id, topic, partition_id, record_offset)
);
CREATE INDEX matching_pending ON matching_admission(session_id) WHERE NOT published;
CREATE TABLE matching_output (
    epoch_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    partition_id INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    ordinal INTEGER NOT NULL,
    session_id UUID,
    event_id UUID NOT NULL,
    event_topic VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    PRIMARY KEY (epoch_id, topic, partition_id, record_offset, ordinal)
);
CREATE TABLE consumer_receipt (
    consumer_name VARCHAR(32) NOT NULL,
    event_id UUID NOT NULL,
    PRIMARY KEY (consumer_name,event_id)
);
CREATE TABLE order_intent (
    order_id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    epoch_id UUID NOT NULL,
    session_id UUID NOT NULL,
    fencing_token BIGINT NOT NULL,
    account_id UUID NOT NULL,
    asset VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount>0),
    request_hash VARCHAR(64) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX order_intent_session ON order_intent(session_id);
