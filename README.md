# ledger-core

[![CI](https://github.com/armando-calz/ledger-core/actions/workflows/ci.yml/badge.svg)](https://github.com/armando-calz/ledger-core/actions/workflows/ci.yml)

A double-entry ledger service for moving money between accounts — correctly, idempotently and safely under concurrency.

> **Status:** 🚧 early development. The roadmap below tracks what is done and what is next.

## Why

Every fintech product — wallets, cards, transfers, exchanges — sits on top of a ledger. A ledger that loses a cent, double-applies a retried request or races two concurrent withdrawals is a production incident. This project is a focused exploration of the techniques that prevent those failures:

- **Double-entry bookkeeping** — every movement is a balanced journal entry; money is never created or destroyed.
- **Integer minor units** — amounts are stored as `long` cents (or the currency's minor unit), never floating point.
- **Idempotency keys** — a retried request returns the original result instead of moving money twice.
- **Concurrency control** — balances stay consistent when many transfers hit the same account at once.
- **Immutability** — entries are append-only; corrections are new, reversing entries.

## Core model

```mermaid
erDiagram
    ACCOUNT ||--o{ POSTING : "has"
    JOURNAL_ENTRY ||--|{ POSTING : "is made of (≥ 2)"
    ACCOUNT {
        uuid id
        string currency
        string type "ASSET | LIABILITY | ..."
    }
    JOURNAL_ENTRY {
        uuid id
        string idempotency_key
        timestamp created_at
    }
    POSTING {
        uuid account_id
        long amount_minor "debit > 0, credit < 0"
    }
```

**Invariant:** for every journal entry, the postings in each currency sum to zero.

Positive amounts are **debits**, negative amounts are **credits**. Customer wallets are *liability* accounts — the money in them is owed to the customer — so a wallet balance increases with credits (see [ADR 0002](docs/adr/0002-account-types-and-sign-convention.md)).

Alice pays Bob `$100.00 MXN` from her wallet: one journal entry, two postings.

| Account | Type | Posting (minor units) | Balance seen by the customer |
|---|---|---|---|
| Alice wallet | Liability | `+10000` (debit) | decreases by 100.00 |
| Bob wallet | Liability | `-10000` (credit) | increases by 100.00 |
| **Sum** | | **`0`** | |

## Tech stack

Java 21 · Spring Boot 4 · Maven · PostgreSQL · Flyway · Testcontainers · Docker · GitHub Actions

## Roadmap

- [x] Project bootstrap
- [x] `Money` value object and currency handling
- [x] Journal entries and postings with the per-currency zero-sum invariant
- [x] Account model: types, currency, status
- [x] PostgreSQL persistence with Flyway migrations, with the invariants also enforced by the database
- [x] REST API: open accounts, post transfers, query balances and movements (OpenAPI docs, RFC 9457 errors)
- [x] Idempotency keys for transfer requests (safe retries, including concurrent ones)
- [x] Overdraft protection (insufficient funds), checked under row locks
- [x] Concurrency control with tests that race parallel transfers (no double spending, no deadlocks)
- [ ] Reversals (corrections as new entries)
- [ ] Docker Compose setup for local run

Design decisions are recorded as ADRs in [`docs/adr`](docs/adr).

## API

Interactive docs at `http://localhost:8080/swagger-ui.html` once the service is running. Amounts are **decimal strings** (`"100.50"`), never floating-point numbers. Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details: `400` for malformed requests, `404` for unknown accounts, and `422` for requests that would break a ledger rule.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/accounts` | Open an account |
| `GET` | `/api/v1/accounts/{id}` | Get an account |
| `GET` | `/api/v1/accounts/{id}/balance` | Balance from the holder's point of view |
| `GET` | `/api/v1/accounts/{id}/movements` | Most recent movements, newest first |
| `POST` | `/api/v1/transfers` | Debit one account and credit another |

```bash
# Alice (a customer wallet) pays Bob 100.00 MXN
curl -s localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 0f8a2c6e-5d1b-4e7a-9c3f-2b6d8e1a4c70' -d '{
  "debitAccountId": "<alice-wallet-id>",
  "creditAccountId": "<bob-wallet-id>",
  "amount": "100.00", "currency": "MXN", "description": "Dinner"
}'
```

```json
{
  "id": "6e5a5ea5-374c-4340-8a36-959c28d225a0",
  "description": "Dinner",
  "postings": [
    { "accountId": "3a30a91d-…", "amount": "100.00",  "currency": "MXN" },
    { "accountId": "c74bb973-…", "amount": "-100.00", "currency": "MXN" }
  ]
}
```

Retrying with the same `Idempotency-Key` returns the same entry with `Idempotent-Replayed: true` and moves no money; reusing the key for a different request is a `422`. See [ADR 0005](docs/adr/0005-idempotency-keys-claimed-in-the-same-transaction.md).

A transfer that would break a ledger rule is rejected without moving any money:

```json
{ "status": 422, "title": "Insufficient funds", "detail": "Insufficient funds in account 3a30a91d-…: available 50.00 MXN, requested 50.01 MXN" }
```

```json
{ "status": 422, "title": "Ledger rule violated", "detail": "Currency mismatch: expected MXN but got USD" }
```

## Concurrency

Every transfer locks the accounts it touches (`SELECT … FOR UPDATE`, always in id order) before checking balances, so concurrent payments cannot spend the same money twice and opposing transfers cannot deadlock. [`LedgerConcurrencyIT`](src/test/java/io/github/armandocalz/ledger/application/LedgerConcurrencyIT.java) fires 500 simultaneous payments at a wallet holding enough for 100. Without the locks, 103–104 of them went through. With them, exactly 100 do. Details in [ADR 0004](docs/adr/0004-overdraft-protection-with-ordered-row-locks.md).

## Running locally

Requirements: JDK 21 (a [`.sdkmanrc`](.sdkmanrc) is included for [SDKMAN!](https://sdkman.io) users) and Docker.

```bash
./mvnw verify              # unit tests + integration tests against a PostgreSQL container
./mvnw spring-boot:test-run # start the service on :8080 with a throwaway PostgreSQL
curl localhost:8080/actuator/health
```

<details>
<summary>Using Colima instead of Docker Desktop</summary>

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```
</details>

## License

[MIT](LICENSE)
