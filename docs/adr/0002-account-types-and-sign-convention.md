# ADR 0002 — Account types and sign convention

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

ADR 0001 stores each posting as a signed amount, and an entry must sum to zero. That leaves two questions open:

1. **What does the sign mean?**
2. **How does "Alice pays Bob" map to postings?**

The first version of `JournalEntry.transfer(from, to, amount)` credited `from` and debited `to`. That is right when both accounts are *assets* (cash moving between bank accounts). It is **wrong for customer wallets**: in a fintech, the money in a customer's wallet is a *liability* (the business owes it to the customer), and a liability decreases with a debit.

## Decision

1. **Debits are positive, credits are negative.** This is a storage convention only.
2. **Every account has a type** — `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE` or `EXPENSE` — and each type has a *normal balance side*:

   | Type | Increases with | Example |
   |---|---|---|
   | Asset | Debit | Cash at the partner bank |
   | Expense | Debit | Card network fees paid |
   | Liability | Credit | Customer wallets |
   | Equity | Credit | Owner's capital |
   | Revenue | Credit | Transfer fees charged |

3. **Transfers name the debit and the credit account explicitly**, `transfer(debitAccount, creditAccount, amount)`, as in TigerBeetle or Modern Treasury. The caller decides which side is which from the account types. The ledger does not guess from words like "from" and "to".
4. **Balances are presented from the holder's point of view** with `AccountType.presentBalance`: a wallet whose postings sum to `-100.00` shows the customer `100.00`.

### Worked example

A customer deposits 500.00, then pays 100.00 to another customer, with a 1.00 fee:

| Entry | Debit | Credit | Amount |
|---|---|---|---|
| Deposit | Cash (asset) | Alice wallet (liability) | 500.00 |
| Payment | Alice wallet | Bob wallet | 100.00 |
| Fee | Alice wallet | Fee revenue | 1.00 |

Result: Alice sees `399.00`, Bob sees `100.00`, revenue is `1.00`, cash is `500.00`. Assets (500) = liabilities (499) + revenue (1).

## Consequences

- ✅ Real accounting semantics: the ledger can be reconciled against bank statements and reported with standard financial statements.
- ✅ The direction of a movement is explicit in the API instead of implied.
- ⚠️ API clients must understand debit/credit. The future REST API will offer intent-level endpoints (e.g. "wallet payment") that map to the right sides, so most clients never deal with them directly.
- ⚠️ An account has exactly one currency and one type for its whole life; changing either means opening a new account.
