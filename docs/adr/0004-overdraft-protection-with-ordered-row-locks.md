# ADR 0004 — Overdraft protection with ordered row locks

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

A customer wallet must never be spent below zero. The obvious implementation is:

1. read the balance,
2. check that it covers the payment,
3. insert the postings.

Under PostgreSQL's default `READ COMMITTED` isolation, that is a **check-then-act race**. Two transactions can both read 100.00, both pass the check and both insert a payment. The money was spent twice.

This is not hypothetical. `LedgerConcurrencyIT` fires 500 concurrent 1.00 payments at a wallet holding 100.00. Against the naive implementation, **103–104 payments succeeded in each of three runs**, and the wallet ended at −3.00 or −4.00.

## Decision

`LedgerService.post` runs in one transaction that:

1. **Locks every account involved** with `SELECT … FOR UPDATE`, **ordered by account id**.
2. Validates each posting against the locked account: currency and status (ADR 0002), then the overdraft rule (`Account.ensureCanApply`).
3. Appends the entry and commits, which releases the locks.

While an account is locked, no other transfer touching it can read-and-check its balance, so the check and the write become atomic. The account's status is also read under the lock, which closes a second race: an account being frozen while a payment to it is in flight.

**Ordered locking prevents deadlocks.** If transfer A→B locked A then B while B→A locked B then A, each would wait for the other forever. Locking in id order means both lock the lower id first. `LedgerConcurrencyIT` runs 200 interleaved A→B / B→A transfers and expects zero failures.

**Overdraft policy.** By default, the balance as seen by the account's holder must stay ≥ 0, for every account type. Internal accounts that legitimately go negative, such as a settlement account with a partner bank, are opened with `allowNegativeBalance = true`. Customer wallets never are.

## Alternatives considered

| Option | Why not (for now) |
|---|---|
| `SERIALIZABLE` isolation | Correct, but conflicts surface as serialization failures that every caller must retry. Under contention on a hot account, retries pile up. |
| Optimistic locking (version column) | Same retry problem. Fits when conflicts are rare; a busy wallet or a fee account is the opposite. |
| Stored balance column + `CHECK (balance >= 0)` | Strong and fast, and likely the next step (see consequences). It adds a mutable column that must stay consistent with postings (ADR 0001 keeps postings as the source of truth). |
| Advisory locks | Work, but they are invisible in the schema and easier to misuse than row locks on the rows that actually matter. |

## Consequences

- ✅ No double spending, verified by a test that reproduces the race without the locks.
- ✅ No deadlocks between transfers, by construction.
- ✅ Freezing an account cannot race with an in-flight payment.
- ⚠️ Transfers on the same account are serialized. That is the price of correctness on a hot account (e.g. a fee account credited by every payment). Mitigations if needed: shard hot internal accounts, or keep credit-only accounts out of the lock set when they cannot go negative.
- ⚠️ The balance is still computed by summing the account's postings while holding the lock, so cost grows with account history. A balance snapshot updated in the same transaction (under the same lock) is the planned optimization.
