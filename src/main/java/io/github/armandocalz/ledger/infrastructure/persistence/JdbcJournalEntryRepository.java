package io.github.armandocalz.ledger.infrastructure.persistence;

import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.AccountMovement;
import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import io.github.armandocalz.ledger.domain.JournalEntryRepository;
import io.github.armandocalz.ledger.domain.Money;
import io.github.armandocalz.ledger.domain.Posting;
import java.time.OffsetDateTime;
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
        jdbc.sql("""
                INSERT INTO journal_entries (id, description, reverses_entry_id)
                VALUES (:id, :description, :reverses)
                """)
                .param("id", entry.id().value())
                .param("description", entry.description())
                .param("reverses", entry.reverses() == null ? null : entry.reverses().value())
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
        record Header(String description, JournalEntryId reverses) {
        }
        Optional<Header> header = jdbc.sql("SELECT description, reverses_entry_id FROM journal_entries WHERE id = :id")
                .param("id", id.value())
                .query((rs, rowNum) -> new Header(rs.getString("description"), toEntryId(rs.getObject("reverses_entry_id", UUID.class))))
                .optional();
        if (header.isEmpty()) {
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
        return Optional.of(new JournalEntry(id, header.get().description(), postings, header.get().reverses()));
    }

    @Override
    public boolean lock(JournalEntryId id) {
        // SELECT ... FOR UPDATE takes a row lock without firing the append-only UPDATE trigger
        return jdbc.sql("SELECT id FROM journal_entries WHERE id = :id FOR UPDATE")
                .param("id", id.value())
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    @Override
    public Optional<JournalEntryId> findReversalOf(JournalEntryId id) {
        return jdbc.sql("SELECT id FROM journal_entries WHERE reverses_entry_id = :id")
                .param("id", id.value())
                .query(UUID.class)
                .optional()
                .map(JournalEntryId::new);
    }

    private static JournalEntryId toEntryId(UUID value) {
        return value == null ? null : new JournalEntryId(value);
    }

    @Override
    public Money rawBalanceOf(Account account) {
        long total = jdbc.sql("SELECT coalesce(sum(amount_minor), 0)::bigint FROM postings WHERE account_id = :id")
                .param("id", account.id().value())
                .query(Long.class)
                .single();
        return Money.ofMinor(total, account.currency());
    }

    @Override
    public List<AccountMovement> movementsOf(Account account, int limit) {
        return jdbc.sql("""
                SELECT e.id, e.description, e.created_at, p.amount_minor
                FROM postings p
                JOIN journal_entries e ON e.id = p.journal_entry_id
                WHERE p.account_id = :accountId
                ORDER BY p.id DESC
                LIMIT :limit
                """)
                .param("accountId", account.id().value())
                .param("limit", limit)
                .query((rs, rowNum) -> new AccountMovement(
                        new JournalEntryId(rs.getObject("id", UUID.class)),
                        rs.getString("description"),
                        Money.ofMinor(rs.getLong("amount_minor"), account.currency()),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
