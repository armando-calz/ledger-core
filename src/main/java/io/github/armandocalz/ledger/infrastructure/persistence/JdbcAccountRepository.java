package io.github.armandocalz.ledger.infrastructure.persistence;

import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.AccountRepository;
import io.github.armandocalz.ledger.domain.AccountStatus;
import io.github.armandocalz.ledger.domain.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final String COLUMNS = "id, name, type, currency, status, allow_negative_balance";

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(Account account) {
        jdbc.sql("""
                INSERT INTO accounts (id, name, type, currency, status, allow_negative_balance)
                VALUES (:id, :name, :type, :currency, :status, :allowNegativeBalance)
                """)
                .param("id", account.id().value())
                .param("name", account.name())
                .param("type", account.type().name())
                .param("currency", account.currency().getCurrencyCode())
                .param("status", account.status().name())
                .param("allowNegativeBalance", account.allowNegativeBalance())
                .update();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE id = :id")
                .param("id", id.value())
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public void updateStatus(Account account) {
        int updated = jdbc.sql("UPDATE accounts SET status = :status WHERE id = :id")
                .param("status", account.status().name())
                .param("id", account.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Account not found: " + account.id());
        }
    }

    private static Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                new AccountId(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("type")),
                Currency.getInstance(rs.getString("currency")),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getBoolean("allow_negative_balance"));
    }
}
