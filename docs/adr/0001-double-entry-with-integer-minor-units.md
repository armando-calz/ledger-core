# ADR 0001 — Double-entry postings with integer minor units

- **Status:** Accepted
- **Date:** 2026-09-28

## Context

The ledger must record money movements so that:

1. no money is ever created or destroyed by a bug,
2. every balance can be explained from its history (auditability),
3. arithmetic is exact — `0.1 + 0.2` must equal `0.3`.

Two decisions shape everything else: **how a movement is recorded** and **how an amount is represented**.

## Decision

### 1. Double-entry journal entries

A movement is a `JournalEntry` made of two or more `Posting`s. Each posting targets one account with a signed amount. An entry is only valid if, **for each currency, its postings sum to zero**. The invariant is enforced in the domain model when the entry is built, and again in the database.

Account balances are **derived** from postings, not stored as mutable fields that can drift from their history.

Entries are **append-only**. A mistake is corrected with a new entry that reverses it, never by updating or deleting rows.

### 2. Amounts as `long` minor units + ISO 4217 currency

Amounts are stored as a `long` count of the currency's minor unit (e.g. `10050` = `100.50 MXN`), together with the ISO 4217 currency code. The number of minor units per currency comes from `java.util.Currency#getDefaultFractionDigits` (MXN/USD → 2, JPY → 0).

## Alternatives considered

| Option | Why not |
|---|---|
| Single-entry (`balance += amount` on one row) | No built-in check that money is conserved; history cannot fully explain a balance. |
| `double` / `float` | Binary floating point cannot represent most decimal fractions exactly; rounding errors accumulate. |
| `BigDecimal` everywhere | Exact, but scale must be policed on every operation, and it is slower and heavier to persist and index. Kept for formatting and parsing only at the API boundary. |
| Stored, mutable balance column as the source of truth | Fast reads, but can drift from history. May be added later as a *cache* that is verified against postings. |

## Consequences

- ✅ Money conservation is a checkable invariant rather than a convention.
- ✅ Exact arithmetic with cheap, indexable `BIGINT` columns.
- ✅ Full audit trail; any balance can be recomputed.
- ⚠️ Reading a balance means aggregating postings. If that becomes slow, add snapshots or a verified balance cache (future ADR).
- ⚠️ `long` limits a single amount to ~9.2 × 10¹⁸ minor units. That is far above any realistic transfer, but overflow must still be checked explicitly (`Math.addExact`).
- ⚠️ The API has to convert between human decimals (`"100.50"`) and minor units, and reject amounts with more precision than the currency allows.
