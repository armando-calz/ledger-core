-- Ledger schema. The database enforces the same invariants as the domain model
-- (ADR 0001, ADR 0002), so a bug or a manual query cannot corrupt the books.

CREATE TABLE accounts (
    id          UUID        PRIMARY KEY,
    name        TEXT        NOT NULL CHECK (btrim(name) <> ''),
    type        TEXT        NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency    CHAR(3)     NOT NULL,
    status      TEXT        NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Target of the composite foreign key below: a posting's currency must match its account's.
    UNIQUE (id, currency)
);

CREATE TABLE journal_entries (
    id          UUID        PRIMARY KEY,
    description TEXT        NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE postings (
    id               BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_entry_id UUID    NOT NULL REFERENCES journal_entries (id),
    account_id       UUID    NOT NULL,
    currency         CHAR(3) NOT NULL,
    amount_minor     BIGINT  NOT NULL CHECK (amount_minor <> 0),
    FOREIGN KEY (account_id, currency) REFERENCES accounts (id, currency)
);

CREATE INDEX postings_account_id_idx ON postings (account_id);
CREATE INDEX postings_journal_entry_id_idx ON postings (journal_entry_id);

-- Zero-sum invariant, checked at COMMIT (deferred) so all postings of an entry
-- can be inserted first. Also requires at least two postings per entry.
CREATE FUNCTION check_journal_entry_balanced() RETURNS trigger AS $$
DECLARE
    posting_count INT;
    unbalanced    TEXT;
BEGIN
    SELECT count(*) INTO posting_count FROM postings WHERE journal_entry_id = NEW.journal_entry_id;
    IF posting_count < 2 THEN
        RAISE EXCEPTION 'Journal entry % has % posting(s); at least 2 are required',
            NEW.journal_entry_id, posting_count USING ERRCODE = 'check_violation';
    END IF;

    SELECT string_agg(currency || ' ' || total, ', ') INTO unbalanced
    FROM (
        SELECT currency, sum(amount_minor) AS total
        FROM postings
        WHERE journal_entry_id = NEW.journal_entry_id
        GROUP BY currency
        HAVING sum(amount_minor) <> 0
    ) AS imbalances;
    IF unbalanced IS NOT NULL THEN
        RAISE EXCEPTION 'Journal entry % is unbalanced: %', NEW.journal_entry_id, unbalanced
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_entry_balanced
    AFTER INSERT ON postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_journal_entry_balanced();

-- The books are append-only: corrections are new, reversing entries.
CREATE FUNCTION reject_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER postings_append_only
    BEFORE UPDATE OR DELETE ON postings
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
