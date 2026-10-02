package io.github.armandocalz.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * A ledger account. Immutable: state changes return a new instance.
 *
 * <p>An account holds a single currency for its whole life; multi-currency holders get one
 * account per currency.
 */
public record Account(AccountId id, String name, AccountType type, Currency currency, AccountStatus status) {

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
        return new Account(id, name, type, currency, AccountStatus.ACTIVE);
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
        return new Account(id, name, type, currency, newStatus);
    }
}
