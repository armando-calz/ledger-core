# ADR 0003 — Persistence with plain JDBC and database-enforced invariants

- **Status:** Accepted
- **Date:** 2026-10-05

## Context

The ledger needs durable storage for accounts, journal entries and postings. Two questions:

1. **Which data-access approach?** Spring offers JPA/Hibernate, Spring Data JDBC and plain `JdbcClient`.
2. **Where are the invariants enforced?** The domain model (ADR 0001, 0002) already rejects unbalanced entries, but a bug in another code path, a migration script or a manual `psql` session could still write bad rows.

## Decision

### Plain SQL through `JdbcClient`

Repositories are written with `JdbcClient` and explicit SQL. The domain stays free of persistence annotations, and repository interfaces live in the domain package. Only the JDBC implementations know about the database.

Why not JPA: a ledger is append-only with a few, very specific queries (aggregate a balance, lock an account row). For that, JPA's dirty checking, lazy loading and generated SQL get in the way. The upcoming concurrency work needs exact control over `SELECT … FOR UPDATE` and transaction boundaries, so the SQL that runs should be the SQL in the code.

### Defense in depth: the database enforces the invariants too

| Invariant | Domain | Database (V1 migration) |
|---|---|---|
| Entry sums to zero per currency | `JournalEntry` constructor | Deferred constraint trigger, checked at `COMMIT` |
| At least 2 postings per entry | `JournalEntry` constructor | Same trigger |
| No zero-amount postings | `Posting` constructor | `CHECK (amount_minor <> 0)` |
| Posting currency = account currency | `Account.ensureAccepts` | Composite FK `(account_id, currency) → accounts (id, currency)` |
| Books are append-only | No update/delete methods | `BEFORE UPDATE OR DELETE` triggers raise an error |
| Valid type/status values | Java enums | `CHECK … IN (…)` |

The zero-sum trigger is `DEFERRABLE INITIALLY DEFERRED`: postings of one entry are inserted one row at a time, so the check has to run once all of them are in.

Integration tests use raw SQL on purpose to bypass the domain and prove that the database rejects each violation.

### Testing against real PostgreSQL

Integration tests (`*IT`, run by Failsafe during `mvn verify`) start a throwaway PostgreSQL with Testcontainers. Triggers, deferred constraints and composite FKs are PostgreSQL features, and an in-memory substitute such as H2 would not test them faithfully.

## Consequences

- ✅ Corrupting the books takes deliberately dropping constraints, not just a bug.
- ✅ SQL is visible, reviewable and tunable.
- ⚠️ More boilerplate than Spring Data (row mappers, explicit inserts).
- ⚠️ Business rules exist in two places. They are deliberately simple, and the integration tests keep both in sync.
- ⚠️ The balance trigger aggregates an entry's postings on every insert. That is cheap for 2–4 postings per entry and should be revisited only if entries become very large.
