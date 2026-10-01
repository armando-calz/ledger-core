package io.github.armandocalz.ledger.domain;

import java.util.Objects;

/**
 * One leg of a journal entry: a signed amount applied to one account.
 * Positive amounts are debits, negative amounts are credits.
 */
public record Posting(AccountId accountId, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            throw new IllegalArgumentException("A posting cannot have a zero amount");
        }
    }

    public static Posting debit(AccountId accountId, Money amount) {
        return new Posting(accountId, requirePositive(amount));
    }

    public static Posting credit(AccountId accountId, Money amount) {
        return new Posting(accountId, requirePositive(amount).negate());
    }

    private static Money requirePositive(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive, got " + amount);
        }
        return amount;
    }
}
