package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.JournalEntryId;

public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException(JournalEntryId id) {
        super("Journal entry not found: " + id);
    }
}
