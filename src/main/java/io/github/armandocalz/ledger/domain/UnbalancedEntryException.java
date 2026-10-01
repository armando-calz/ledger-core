package io.github.armandocalz.ledger.domain;

import java.util.Collection;

/** Thrown when the postings of a journal entry do not sum to zero in every currency. */
public class UnbalancedEntryException extends IllegalArgumentException {

    public UnbalancedEntryException(Collection<Money> imbalances) {
        super("Journal entry is unbalanced: " + imbalances);
    }
}
