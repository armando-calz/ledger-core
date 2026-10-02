package io.github.armandocalz.ledger.domain;

/** Thrown when a posting targets an account that is frozen or closed. */
public class AccountNotActiveException extends IllegalStateException {

    public AccountNotActiveException(AccountId id, AccountStatus status) {
        super("Account %s is %s and cannot accept postings".formatted(id, status));
    }
}
