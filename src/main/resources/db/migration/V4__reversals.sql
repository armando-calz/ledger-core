-- Reversals (ADR 0006): a reversal is a new entry linked to the one it undoes.
-- UNIQUE guarantees an entry is reversed at most once, even under concurrency.
ALTER TABLE journal_entries
    ADD COLUMN reverses_entry_id UUID UNIQUE REFERENCES journal_entries (id),
    ADD CONSTRAINT journal_entries_no_self_reversal CHECK (reverses_entry_id <> id);
