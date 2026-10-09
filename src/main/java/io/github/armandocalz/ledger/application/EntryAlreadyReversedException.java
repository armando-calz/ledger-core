package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.JournalEntryId;

/** Thrown when reversing an entry that already has a reversal. */
public class EntryAlreadyReversedException extends RuntimeException {

    private final JournalEntryId reversal;

    public EntryAlreadyReversedException(JournalEntryId entry, JournalEntryId reversal) {
        super("Journal entry %s was already reversed by %s".formatted(entry, reversal));
        this.reversal = reversal;
    }

    public JournalEntryId reversal() {
        return reversal;
    }
}
