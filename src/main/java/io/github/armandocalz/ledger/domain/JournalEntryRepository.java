package io.github.armandocalz.ledger.domain;

import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository {

    /** Appends an entry and all its postings atomically. Entries are never updated or deleted. */
    void append(JournalEntry entry);

    Optional<JournalEntry> findById(JournalEntryId id);

    /**
     * Locks the entry until the current transaction ends, serializing operations on it
     * (e.g. two attempts to reverse it). Returns {@code false} if the entry does not exist.
     */
    boolean lock(JournalEntryId id);

    /** The id of the entry that reverses {@code id}, if any. */
    Optional<JournalEntryId> findReversalOf(JournalEntryId id);

    /** Sum of all postings of the account, debits positive (see {@link AccountType#presentBalance}). */
    Money rawBalanceOf(Account account);

    /** The account's most recent movements, newest first. */
    List<AccountMovement> movementsOf(Account account, int limit);
}
