# ADR 0005 — Idempotency keys claimed in the same transaction

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

Networks fail after the server has done the work. A client posts a transfer, the ledger commits it, and the response is lost to a timeout. The client cannot tell whether the money moved, so it retries, and a naive server moves the money twice.

Clients need a way to say *"this is the same transfer as before"*, and the server needs to honor it, even when the retry arrives **while the original is still being processed**.

## Decision

Clients send an `Idempotency-Key` header (1–255 characters, e.g. a UUID) with `POST /api/v1/transfers`. In the **same database transaction** that posts the entry, the service:

1. **Claims the key:** `INSERT INTO idempotency_keys … ON CONFLICT (key) DO NOTHING`, storing a SHA-256 fingerprint of the request.
2. If the insert succeeded, posts the entry as usual (ADR 0004 locks included), then records the entry id on the key.
3. If the key already existed:
   - same fingerprint → returns the **original entry** with `Idempotent-Replayed: true` and moves no money;
   - different fingerprint → `422 Idempotency key reused`.

### Why the claim is race-free

If two requests with the same key arrive together, the second `INSERT … ON CONFLICT DO NOTHING` **blocks** on the first transaction's uncommitted row. When the first commits, the second skips the insert, reads the stored entry and replays it. When the first rolls back, the second's insert goes through and it executes normally. `IdempotencyIT` fires 30 simultaneous requests with one key and asserts that exactly one posts and 29 replay the same entry.

### Failures do not consume the key

Because the claim and the entry share a transaction, a request rejected for a business reason (e.g. insufficient funds) rolls back its claim. A retry after the client tops up the wallet runs normally. This is intentional: those failures are worth retrying.

### Fingerprint

All fields (debit account, credit account, amount in minor units, currency, description) are length-prefixed and hashed with SHA-256. `"10.0"` and `"10.00"` are the same request. Moving a byte from one field to another produces a different input.

## Alternatives considered

| Option | Why not |
|---|---|
| Check for the key, then insert it (two statements) | Two concurrent retries can both see "not found" and both execute. |
| Store keys in Redis with a TTL | A second system that must stay consistent with the database; a crash between the Redis write and the commit breaks the guarantee. |
| Cache the full HTTP response, errors included (Stripe's model) | More general, but replaying a 422 forever would block a legitimate retry after the client fixes the cause. |
| Natural-key deduplication (e.g. unique description) | Two genuinely identical payments, such as two coffees, are valid and must not be merged. |

## Consequences

- ✅ Retries are safe, including concurrent ones, with no extra infrastructure.
- ✅ Keys and entries can never disagree: they commit or roll back together.
- ⚠️ Keys are global. With multiple API clients they should be scoped per client to avoid collisions.
- ⚠️ Keys are kept forever. A retention job (e.g. delete after 24–72 h) is needed before real traffic.
- ⚠️ The key is optional, so transfers without one are not deduplicated. A production API would likely require it.
