package io.github.armandocalz.ledger.infrastructure.persistence;

import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import io.github.armandocalz.ledger.domain.JournalEntryRepository;
import io.github.armandocalz.ledger.domain.Money;
import io.github.armandocalz.ledger.domain.Posting;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcJournalEntryRepository implements JournalEntryRepository {

    private final JdbcClient jdbc;

    JdbcJournalEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void append(JournalEntry entry) {
        jdbc.sql("INSERT INTO journal_entries (id, description) VALUES (:id, :description)")
                .param("id", entry.id().value())
                .param("description", entry.description())
                .update();
        for (Posting posting : entry.postings()) {
            jdbc.sql("""
                    INSERT INTO postings (journal_entry_id, account_id, currency, amount_minor)
                    VALUES (:entryId, :accountId, :currency, :amountMinor)
                    """)
                    .param("entryId", entry.id().value())
                    .param("accountId", posting.accountId().value())
                    .param("currency", posting.amount().currency().getCurrencyCode())
                    .param("amountMinor", posting.amount().amountMinor())
                    .update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JournalEntry> findById(JournalEntryId id) {
        Optional<String> description = jdbc.sql("SELECT description FROM journal_entries WHERE id = :id")
                .param("id", id.value())
                .query(String.class)
                .optional();
        if (description.isEmpty()) {
            return Optional.empty();
        }
        List<Posting> postings = jdbc.sql("""
                SELECT account_id, currency, amount_minor
                FROM postings
                WHERE journal_entry_id = :id
                ORDER BY id
                """)
                .param("id", id.value())
                .query((rs, rowNum) -> new Posting(
                        new AccountId(rs.getObject("account_id", UUID.class)),
                        Money.ofMinor(rs.getLong("amount_minor"), Currency.getInstance(rs.getString("currency")))))
                .list();
        return Optional.of(new JournalEntry(id, description.get(), postings));
    }

    @Override
    public Money rawBalanceOf(Account account) {
        long total = jdbc.sql("SELECT coalesce(sum(amount_minor), 0)::bigint FROM postings WHERE account_id = :id")
                .param("id", account.id().value())
                .query(Long.class)
                .single();
        return Money.ofMinor(total, account.currency());
    }
}
