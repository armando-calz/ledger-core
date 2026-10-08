package io.github.armandocalz.ledger.infrastructure.persistence;

import io.github.armandocalz.ledger.application.IdempotencyKeyRepository;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final JdbcClient jdbc;

    JdbcIdempotencyKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryClaim(String key, String requestHash) {
        // ON CONFLICT DO NOTHING blocks while another transaction holds an uncommitted row with
        // the same key, then skips the insert if that transaction committed.
        return jdbc.sql("""
                INSERT INTO idempotency_keys (key, request_hash) VALUES (:key, :hash)
                ON CONFLICT (key) DO NOTHING
                """)
                .param("key", key)
                .param("hash", requestHash)
                .update() == 1;
    }

    @Override
    public Optional<StoredKey> find(String key) {
        return jdbc.sql("SELECT request_hash, journal_entry_id FROM idempotency_keys WHERE key = :key")
                .param("key", key)
                .query((rs, rowNum) -> {
                    UUID entryId = rs.getObject("journal_entry_id", UUID.class);
                    return new StoredKey(rs.getString("request_hash"), entryId == null ? null : new JournalEntryId(entryId));
                })
                .optional();
    }

    @Override
    public void complete(String key, JournalEntryId journalEntryId) {
        jdbc.sql("UPDATE idempotency_keys SET journal_entry_id = :entryId WHERE key = :key")
                .param("entryId", journalEntryId.value())
                .param("key", key)
                .update();
    }
}
