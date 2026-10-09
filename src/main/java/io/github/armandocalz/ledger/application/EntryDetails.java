package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;

/** An entry plus the id of the entry that reversed it, if any. */
public record EntryDetails(JournalEntry entry, JournalEntryId reversedBy) {
}
