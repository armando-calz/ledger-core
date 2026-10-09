# ADR 0006 — Reversals as linked, append-only entries

- **Status:** Accepted
- **Date:** 2026-10-08

## Context

Payments sometimes have to be undone: a deposit bounces, a merchant refunds, an operator posted the wrong amount. ADR 0001 makes the books append-only (database triggers reject `UPDATE` and `DELETE`), so a correction cannot edit or remove the original entry.

The operation also has to be safe: undoing a payment twice would give the payer money that never existed.

## Decision

A **reversal is a new journal entry** with the same postings as the original, every amount negated, and a `reverses_entry_id` link to it.

- **The original is never modified.** History shows the payment, then its reversal. Balances net out because postings are summed.
- **At most one reversal per entry**, enforced twice:
  1. `reverse()` locks the original entry row with `SELECT … FOR UPDATE` (a row lock that does not fire the append-only `UPDATE` trigger) and checks for an existing reversal while holding it. Concurrent attempts queue up, and all but the first get a clear `409 Transfer already reversed` with the existing `reversalId`.
  2. A `UNIQUE` constraint on `reverses_entry_id` is the database's guarantee if any other code path tries.

  `ReversalIT` fires 20 concurrent reversals of one entry and expects exactly one success. With the entry lock removed, the other 19 fail on the `UNIQUE` constraint instead. Money stays safe either way, but the error is unhelpful.
- **A reversal is a normal entry.** It goes through the same account locks and checks (ADR 0004): currency, status and overdraft. If Bob already spent the money Alice paid him, reversing the payment would overdraw Bob's wallet, so it is rejected.
- **Reversals cannot be reversed.** To redo an operation, post a new entry. This keeps the chain one level deep and easy to audit.
- Partial reversals (refunding part of a payment) are expressed as a new transfer in the opposite direction, not as a reversal.

## Consequences

- ✅ The full story of every correction stays in the ledger.
- ✅ No double reversals, verified under concurrency.
- ⚠️ Rejecting a reversal for insufficient funds is right for wallets, but real chargebacks sometimes must go through anyway and leave the account negative, to be collected later. That would need an explicit "forced" reversal on an account allowed to go negative. It is out of scope here.
- ⚠️ Reversals lock the entry and then its accounts. Transfers only lock accounts, so the lock order stays acyclic and cannot deadlock.
