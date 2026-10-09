package io.github.armandocalz.ledger.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.armandocalz.ledger.PostgresTestcontainer;
import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.AccountRepository;
import io.github.armandocalz.ledger.domain.AccountStatus;
import io.github.armandocalz.ledger.domain.AccountType;
import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import io.github.armandocalz.ledger.domain.JournalEntryRepository;
import io.github.armandocalz.ledger.domain.Money;
import io.github.armandocalz.ledger.domain.Posting;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class LedgerPersistenceIT {

    private static final Currency MXN = Currency.getInstance("MXN");

    @Autowired AccountRepository accounts;
    @Autowired JournalEntryRepository entries;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    private Account openWallet(String name) {
        Account account = Account.open(AccountId.random(), name, AccountType.LIABILITY, MXN);
        accounts.create(account);
        return account;
    }

    @Nested
    class Accounts {

        @Test
        void roundTrips() {
            Account wallet = openWallet("Alice wallet");

            assertThat(accounts.findById(wallet.id())).contains(wallet);
            assertThat(accounts.findById(AccountId.random())).isEmpty();
        }

        @Test
        void persistsStatusChanges() {
            Account wallet = openWallet("Alice wallet");

            accounts.updateStatus(wallet.freeze());

            assertThat(accounts.findById(wallet.id())).get()
                    .extracting(Account::status).isEqualTo(AccountStatus.FROZEN);
        }
    }

    @Nested
    class JournalEntries {

        @Test
        void appendsAndReadsBackAnEntry() {
            Account alice = openWallet("Alice wallet");
            Account bob = openWallet("Bob wallet");
            JournalEntry payment = JournalEntry.transfer(
                    JournalEntryId.random(), alice.id(), bob.id(), Money.of("100.00", MXN), "Dinner");

            entries.append(payment);

            assertThat(entries.findById(payment.id())).contains(payment);
            assertThat(entries.findById(JournalEntryId.random())).isEmpty();
        }

        @Test
        void derivesBalancesFromPostings() {
            Account cash = Account.open(AccountId.random(), "Cash at bank", AccountType.ASSET, MXN);
            accounts.create(cash);
            Account alice = openWallet("Alice wallet");
            Account bob = openWallet("Bob wallet");

            entries.append(JournalEntry.transfer(JournalEntryId.random(), cash.id(), alice.id(), Money.of("500.00", MXN), "Deposit"));
            entries.append(JournalEntry.transfer(JournalEntryId.random(), alice.id(), bob.id(), Money.of("100.00", MXN), "Payment"));

            assertThat(alice.type().presentBalance(entries.rawBalanceOf(alice))).isEqualTo(Money.of("400.00", MXN));
            assertThat(bob.type().presentBalance(entries.rawBalanceOf(bob))).isEqualTo(Money.of("100.00", MXN));
            assertThat(cash.type().presentBalance(entries.rawBalanceOf(cash))).isEqualTo(Money.of("500.00", MXN));
        }

        @Test
        void accountWithoutPostingsHasZeroBalance() {
            Account wallet = openWallet("Empty wallet");

            assertThat(entries.rawBalanceOf(wallet)).isEqualTo(Money.zero(MXN));
        }
    }

    /**
     * These tests bypass the domain model with raw SQL to prove the database itself
     * refuses to corrupt the books.
     */
    @Nested
    class DatabaseInvariants {

        @Test
        void rejectsUnbalancedEntriesAtCommit() {
            Account alice = openWallet("Alice wallet");
            Account bob = openWallet("Bob wallet");

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                UUID entryId = insertEntry();
                insertPosting(entryId, alice.id(), "MXN", 10000);
                insertPosting(entryId, bob.id(), "MXN", -9999);
            }))
                    .rootCause().hasMessageContaining("unbalanced");
        }

        @Test
        void rejectsSinglePostingEntries() {
            Account alice = openWallet("Alice wallet");

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                UUID entryId = insertEntry();
                insertPosting(entryId, alice.id(), "MXN", 10000);
            }))
                    .rootCause().hasMessageContaining("at least 2");
        }

        @Test
        void rejectsPostingsInADifferentCurrencyThanTheAccount() {
            Account alice = openWallet("Alice wallet");
            Account bob = openWallet("Bob wallet");

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                UUID entryId = insertEntry();
                insertPosting(entryId, alice.id(), "USD", 10000);
                insertPosting(entryId, bob.id(), "USD", -10000);
            }))
                    .rootCause().hasMessageContaining("foreign key");
        }

        @Test
        void isAppendOnly() {
            Account alice = openWallet("Alice wallet");
            Account bob = openWallet("Bob wallet");
            JournalEntry payment = JournalEntry.transfer(
                    JournalEntryId.random(), alice.id(), bob.id(), Money.of("10.00", MXN), "");
            entries.append(payment);

            assertThatThrownBy(() -> jdbc.sql("UPDATE postings SET amount_minor = 1 WHERE journal_entry_id = :id")
                    .param("id", payment.id().value()).update())
                    .rootCause().hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal_entries WHERE id = :id")
                    .param("id", payment.id().value()).update())
                    .rootCause().hasMessageContaining("append-only");

            assertThat(entries.findById(payment.id())).contains(payment);
        }

        @Test
        void allowsAtMostOneReversalPerEntry() {
            Account alice = openWallet("Alice wallet");
            Account bob = openWallet("Bob wallet");
            JournalEntry payment = JournalEntry.transfer(
                    JournalEntryId.random(), alice.id(), bob.id(), Money.of("10.00", MXN), "");
            entries.append(payment);
            entries.append(payment.reversal(JournalEntryId.random(), ""));

            assertThatThrownBy(() -> jdbc.sql("INSERT INTO journal_entries (id, reverses_entry_id) VALUES (:id, :reverses)")
                    .param("id", UUID.randomUUID())
                    .param("reverses", payment.id().value())
                    .update())
                    .rootCause().hasMessageContaining("duplicate key");
        }

        private UUID insertEntry() {
            UUID id = UUID.randomUUID();
            jdbc.sql("INSERT INTO journal_entries (id) VALUES (:id)").param("id", id).update();
            return id;
        }

        private void insertPosting(UUID entryId, AccountId accountId, String currency, long amountMinor) {
            jdbc.sql("""
                    INSERT INTO postings (journal_entry_id, account_id, currency, amount_minor)
                    VALUES (:entryId, :accountId, :currency, :amount)
                    """)
                    .param("entryId", entryId)
                    .param("accountId", accountId.value())
                    .param("currency", currency)
                    .param("amount", amountMinor)
                    .update();
        }
    }

    @Test
    void domainRejectsUnbalancedEntriesBeforeTheyReachTheDatabase() {
        Account alice = openWallet("Alice wallet");
        Account bob = openWallet("Bob wallet");

        assertThatThrownBy(() -> entries.append(new JournalEntry(JournalEntryId.random(), "", List.of(
                Posting.debit(alice.id(), Money.of("1.00", MXN)),
                Posting.credit(bob.id(), Money.of("2.00", MXN))))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
