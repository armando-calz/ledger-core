package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.JournalEntry;

/** The posted entry, and whether it was replayed from an earlier request with the same idempotency key. */
public record TransferResult(JournalEntry entry, boolean replayed) {
}
