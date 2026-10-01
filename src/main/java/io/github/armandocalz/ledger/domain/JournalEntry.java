package io.github.armandocalz.ledger.domain;

import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, balanced set of postings: the only way money moves in the ledger.
 *
 * <p>Invariant: for each currency, the postings sum to zero (see ADR 0001). An entry that
 * would break it cannot be constructed.
 */
public record JournalEntry(JournalEntryId id, String description, List<Posting> postings) {

    public JournalEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postings, "postings");
        description = description == null ? "" : description.strip();
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least 2 postings, got " + postings.size());
        }
        requireBalanced(postings);
    }

    /** A simple transfer: {@code amount} leaves {@code from} and arrives at {@code to}. */
    public static JournalEntry transfer(JournalEntryId id, AccountId from, AccountId to, Money amount, String description) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot transfer to the same account: " + from);
        }
        return new JournalEntry(id, description, List.of(
                Posting.credit(from, amount),
                Posting.debit(to, amount)));
    }

    private static void requireBalanced(List<Posting> postings) {
        Map<Currency, Money> totals = new LinkedHashMap<>();
        for (Posting posting : postings) {
            totals.merge(posting.amount().currency(), posting.amount(), Money::plus);
        }
        List<Money> imbalances = totals.values().stream().filter(total -> !total.isZero()).toList();
        if (!imbalances.isEmpty()) {
            throw new UnbalancedEntryException(imbalances);
        }
    }
}
