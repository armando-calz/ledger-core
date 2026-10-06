package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.AccountId;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(AccountId id) {
        super("Account not found: " + id);
    }
}
