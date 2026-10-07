-- Overdraft policy per account (ADR 0004). Customer wallets keep the default: never negative.
ALTER TABLE accounts ADD COLUMN allow_negative_balance BOOLEAN NOT NULL DEFAULT FALSE;
