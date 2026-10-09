package io.github.armandocalz.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.armandocalz.ledger.PostgresTestcontainer;
import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountType;
import io.github.armandocalz.ledger.domain.InsufficientFundsException;
import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import io.github.armandocalz.ledger.domain.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class ReversalIT {

    private static final Currency MXN = Currency.getInstance("MXN");

    @Autowired LedgerService ledger;

    private Account wallet(String name, String funds) {
        Account wallet = ledger.openAccount(name, AccountType.LIABILITY, MXN);
        if (funds != null) {
            Account cash = ledger.openAccount("Cash for " + name, AccountType.ASSET, MXN);
            ledger.transfer(cash.id(), wallet.id(), Money.of(funds, MXN), "Deposit");
        }
        return wallet;
    }

    @Test
    void restoresBalancesAndKeepsTheOriginal() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        JournalEntry payment = ledger.transfer(alice.id(), bob.id(), Money.of("40.00", MXN), "Dinner");

        JournalEntry reversal = ledger.reverse(payment.id(), "Refund");

        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("100.00", MXN));
        assertThat(ledger.balanceOf(bob.id())).isEqualTo(Money.zero(MXN));
        assertThat(reversal.reverses()).isEqualTo(payment.id());
        assertThat(ledger.getEntry(payment.id()))
                .isEqualTo(new EntryDetails(payment, reversal.id()));
        assertThat(ledger.movementsOf(alice.id(), 10)).extracting(m -> m.description())
                .containsExactly("Refund", "Dinner", "Deposit");
    }

    @Test
    void anEntryCanOnlyBeReversedOnce() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        JournalEntry payment = ledger.transfer(alice.id(), bob.id(), Money.of("40.00", MXN), "Dinner");
        JournalEntry reversal = ledger.reverse(payment.id(), "");

        assertThatThrownBy(() -> ledger.reverse(payment.id(), ""))
                .isInstanceOf(EntryAlreadyReversedException.class)
                .satisfies(e -> assertThat(((EntryAlreadyReversedException) e).reversal()).isEqualTo(reversal.id()));
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("100.00", MXN));
    }

    @Test
    void aReversalCannotBeReversed() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        JournalEntry reversal = ledger.reverse(
                ledger.transfer(alice.id(), bob.id(), Money.of("40.00", MXN), "Dinner").id(), "");

        assertThatThrownBy(() -> ledger.reverse(reversal.id(), "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotReverseWhenTheRecipientAlreadySpentTheMoney() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        Account carol = wallet("Carol wallet", null);
        JournalEntry payment = ledger.transfer(alice.id(), bob.id(), Money.of("40.00", MXN), "Dinner");
        ledger.transfer(bob.id(), carol.id(), Money.of("30.00", MXN), "Taxi");

        assertThatThrownBy(() -> ledger.reverse(payment.id(), "")).isInstanceOf(InsufficientFundsException.class);
        assertThat(ledger.getEntry(payment.id()).reversedBy()).isNull();
    }

    @Test
    void unknownEntryIsNotFound() {
        assertThatThrownBy(() -> ledger.reverse(JournalEntryId.random(), "")).isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    void concurrentReversalsOfTheSameEntrySucceedOnce() throws Exception {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", "500.00");
        JournalEntry payment = ledger.transfer(alice.id(), bob.id(), Money.of("40.00", MXN), "Dinner");

        CountDownLatch start = new CountDownLatch(1);
        List<Future<JournalEntry>> futures = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return ledger.reverse(payment.id(), "");
                }));
            }
            start.countDown();
            for (Future<JournalEntry> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        }

        assertThat(failures).hasSize(19).allMatch(EntryAlreadyReversedException.class::isInstance);
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("100.00", MXN));
        assertThat(ledger.balanceOf(bob.id())).isEqualTo(Money.of("500.00", MXN));
    }
}
