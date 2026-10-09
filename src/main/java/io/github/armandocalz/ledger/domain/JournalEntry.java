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
 *
 * <p>{@code reverses} is set when this entry is the reversal of another one (ADR 0006).
 */
public record JournalEntry(JournalEntryId id, String description, List<Posting> postings, JournalEntryId reverses) {

    public JournalEntry(JournalEntryId id, String description, List<Posting> postings) {
        this(id, description, postings, null);
    }

    public JournalEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postings, "postings");
        description = description == null ? "" : description.strip();
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least 2 postings, got " + postings.size());
        }
        requireBalanced(postings);
        if (id.equals(reverses)) {
            throw new IllegalArgumentException("An entry cannot reverse itself: " + id);
        }
    }

    public boolean isReversal() {
        return reverses != null;
    }

    /**
     * A new entry that undoes this one: same accounts, every amount negated. The original
     * stays untouched, since the books are append-only.
     *
     * @throws IllegalStateException if this entry is itself a reversal
     */
    public JournalEntry reversal(JournalEntryId reversalId, String description) {
        if (isReversal()) {
            throw new IllegalStateException("Entry %s is a reversal and cannot be reversed; post a new entry instead".formatted(id));
        }
        List<Posting> negated = postings.stream()
                .map(posting -> new Posting(posting.accountId(), posting.amount().negate()))
                .toList();
        String text = description == null || description.isBlank() ? "Reversal of " + id : description;
        return new JournalEntry(reversalId, text, negated, id);
    }

    /**
     * A two-leg entry that debits one account and credits another by the same amount.
     *
     * <p>Which side "sends" money depends on the account types (ADR 0002). Between two
     * customer wallets (liabilities), a payment from Alice to Bob debits Alice and credits Bob.
     */
    public static JournalEntry transfer(
            JournalEntryId id, AccountId debitAccount, AccountId creditAccount, Money amount, String description) {
        Objects.requireNonNull(debitAccount, "debitAccount");
        Objects.requireNonNull(creditAccount, "creditAccount");
        if (debitAccount.equals(creditAccount)) {
            throw new IllegalArgumentException("Cannot debit and credit the same account: " + debitAccount);
        }
        return new JournalEntry(id, description, List.of(
                Posting.debit(debitAccount, amount),
                Posting.credit(creditAccount, amount)));
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
