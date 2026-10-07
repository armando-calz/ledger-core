package io.github.armandocalz.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * A ledger account. Immutable: state changes return a new instance.
 *
 * <p>An account holds a single currency for its whole life; multi-currency holders get one
 * account per currency.
 *
 * <p>Unless {@code allowNegativeBalance} is set, the balance seen by the holder can never go
 * below zero. Customer wallets must never allow it; some internal accounts (e.g. a settlement
 * account with a partner bank) may.
 */
public record Account(
        AccountId id, String name, AccountType type, Currency currency, AccountStatus status, boolean allowNegativeBalance) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name is required");
        }
        name = name.strip();
    }

    public static Account open(AccountId id, String name, AccountType type, Currency currency) {
        return open(id, name, type, currency, false);
    }

    public static Account open(AccountId id, String name, AccountType type, Currency currency, boolean allowNegativeBalance) {
        return new Account(id, name, type, currency, AccountStatus.ACTIVE, allowNegativeBalance);
    }

    /**
     * Checks that this account can take the given posting.
     *
     * @throws IllegalArgumentException   if the posting targets another account
     * @throws CurrencyMismatchException  if the posting is in a different currency
     * @throws AccountNotActiveException  if the account is frozen or closed
     */
    public void ensureAccepts(Posting posting) {
        Objects.requireNonNull(posting, "posting");
        if (!id.equals(posting.accountId())) {
            throw new IllegalArgumentException("Posting targets %s, not %s".formatted(posting.accountId(), id));
        }
        if (!currency.equals(posting.amount().currency())) {
            throw new CurrencyMismatchException(currency, posting.amount().currency());
        }
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id, status);
        }
    }

    /**
     * Checks that applying {@code delta} (raw, debits positive) to an account whose raw balance
     * is {@code currentRawBalance} keeps the holder's balance at or above zero.
     *
     * <p>Callers must hold a lock on the account while checking and applying the change,
     * otherwise two concurrent withdrawals can both pass the check (ADR 0004).
     *
     * @throws InsufficientFundsException if the resulting balance would be negative
     */
    public void ensureCanApply(Money currentRawBalance, Money delta) {
        Money resulting = type.presentBalance(currentRawBalance.plus(delta));
        if (resulting.isNegative() && !allowNegativeBalance) {
            throw new InsufficientFundsException(id, type.presentBalance(currentRawBalance), type.presentBalance(delta).negate());
        }
    }

    public Account freeze() {
        return transition(AccountStatus.ACTIVE, AccountStatus.FROZEN);
    }

    public Account unfreeze() {
        return transition(AccountStatus.FROZEN, AccountStatus.ACTIVE);
    }

    public Account close() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Account %s is already closed".formatted(id));
        }
        return withStatus(AccountStatus.CLOSED);
    }

    private Account transition(AccountStatus from, AccountStatus to) {
        if (status != from) {
            throw new IllegalStateException("Cannot change account %s from %s to %s".formatted(id, status, to));
        }
        return withStatus(to);
    }

    private Account withStatus(AccountStatus newStatus) {
        return new Account(id, name, type, currency, newStatus, allowNegativeBalance);
    }
}
