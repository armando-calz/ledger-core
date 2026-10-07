package io.github.armandocalz.ledger.domain;

/** Thrown when a posting would take an account below zero and the account does not allow it. */
public class InsufficientFundsException extends IllegalStateException {

    public InsufficientFundsException(AccountId id, Money available, Money requested) {
        super("Insufficient funds in account %s: available %s, requested %s".formatted(id, available, requested));
    }
}
