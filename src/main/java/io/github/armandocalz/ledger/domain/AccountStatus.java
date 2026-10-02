package io.github.armandocalz.ledger.domain;

public enum AccountStatus {
    /** Accepts postings. */
    ACTIVE,
    /** Temporarily blocked (e.g. fraud review); can be reactivated. */
    FROZEN,
    /** Permanently closed; terminal state. */
    CLOSED
}
