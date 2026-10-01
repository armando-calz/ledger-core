package io.github.armandocalz.ledger.domain;

import java.util.Currency;

/** Thrown when an operation combines amounts in different currencies. */
public class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected %s but got %s".formatted(expected, actual));
    }
}
