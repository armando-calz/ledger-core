-- Idempotency keys for transfer requests (ADR 0005). A key is claimed in the same
-- transaction that posts the entry, so either both are committed or neither is.
CREATE TABLE idempotency_keys (
    key              TEXT        PRIMARY KEY CHECK (length(key) BETWEEN 1 AND 255),
    request_hash     TEXT        NOT NULL,
    journal_entry_id UUID        REFERENCES journal_entries (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
