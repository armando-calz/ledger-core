package io.github.armandocalz.ledger.domain;

import java.util.Optional;

public interface JournalEntryRepository {

    /** Appends an entry and all its postings atomically. Entries are never updated or deleted. */
    void append(JournalEntry entry);

    Optional<JournalEntry> findById(JournalEntryId id);

    /** Sum of all postings of the account, debits positive (see {@link AccountType#presentBalance}). */
    Money rawBalanceOf(Account account);
}
