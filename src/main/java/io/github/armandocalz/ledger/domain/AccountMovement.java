package io.github.armandocalz.ledger.domain;

import java.time.Instant;

/** A posting as seen from one account's history. {@code amount} is raw (debits positive). */
public record AccountMovement(JournalEntryId entryId, String description, Money amount, Instant createdAt) {
}
