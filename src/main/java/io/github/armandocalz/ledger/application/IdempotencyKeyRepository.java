package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.JournalEntryId;
import java.util.Optional;

public interface IdempotencyKeyRepository {

    record StoredKey(String requestHash, JournalEntryId journalEntryId) {
    }

    /**
     * Claims the key for the current transaction. Returns {@code false} if it was already
     * claimed. If another transaction holds an uncommitted claim, waits for it to finish first.
     */
    boolean tryClaim(String key, String requestHash);

    Optional<StoredKey> find(String key);

    /** Records the entry posted for a key claimed in the current transaction. */
    void complete(String key, JournalEntryId journalEntryId);
}
