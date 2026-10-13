# Changelog

## [0.1.0] - 2026-10-12

First release: a double-entry ledger with the guarantees a money-moving service needs.

### Ledger model
- Exact money as `long` minor units with ISO 4217 currencies; overflow fails fast ([ADR 0001](docs/adr/0001-double-entry-with-integer-minor-units.md)).
- Balanced journal entries: postings sum to zero per currency.
- Account types with normal balance sides, so customer wallets are liabilities ([ADR 0002](docs/adr/0002-account-types-and-sign-convention.md)).
- Single-currency accounts with an ACTIVE / FROZEN / CLOSED lifecycle.

### Correctness under failure and concurrency
- Invariants enforced again by PostgreSQL: deferred zero-sum trigger, composite currency foreign key, append-only triggers ([ADR 0003](docs/adr/0003-persistence-with-jdbc-and-database-invariants.md)).
- Overdraft protection under ordered row locks: no double spending, no deadlocks ([ADR 0004](docs/adr/0004-overdraft-protection-with-ordered-row-locks.md)).
- Idempotency keys claimed in the same transaction as the entry, safe under concurrent retries ([ADR 0005](docs/adr/0005-idempotency-keys-claimed-in-the-same-transaction.md)).
- Reversals as linked entries, at most once per entry ([ADR 0006](docs/adr/0006-reversals-as-linked-entries.md)).

### API and operations
- REST API with OpenAPI / Swagger UI and RFC 9457 problem details.
- Docker image (non-root, layered) and Docker Compose stack with PostgreSQL.
- CI: unit and integration tests against real PostgreSQL (Testcontainers), plus an image build and Compose smoke test.

[0.1.0]: https://github.com/armando-calz/ledger-core/releases/tag/v0.1.0
