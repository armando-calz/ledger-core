package io.github.armandocalz.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.armandocalz.ledger.PostgresTestcontainer;
import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountType;
import io.github.armandocalz.ledger.domain.InsufficientFundsException;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import io.github.armandocalz.ledger.domain.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class IdempotencyIT {

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

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void retryReturnsTheOriginalEntryWithoutMovingMoneyAgain() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        TransferCommand payment = new TransferCommand(alice.id(), bob.id(), Money.of("30.00", MXN), "Dinner");
        String key = newKey();

        TransferResult first = ledger.transfer(payment, key);
        TransferResult retry = ledger.transfer(payment, key);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.entry()).isEqualTo(first.entry());
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("70.00", MXN));
        assertThat(ledger.balanceOf(bob.id())).isEqualTo(Money.of("30.00", MXN));
    }

    @Test
    void rejectsAKeyReusedForADifferentRequest() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        String key = newKey();
        ledger.transfer(new TransferCommand(alice.id(), bob.id(), Money.of("30.00", MXN), "Dinner"), key);

        assertThatThrownBy(() -> ledger.transfer(
                new TransferCommand(alice.id(), bob.id(), Money.of("31.00", MXN), "Dinner"), key))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("70.00", MXN));
    }

    @Test
    void failedRequestsDoNotConsumeTheKey() {
        Account alice = wallet("Alice wallet", null);
        Account bob = wallet("Bob wallet", null);
        TransferCommand payment = new TransferCommand(alice.id(), bob.id(), Money.of("30.00", MXN), "Dinner");
        String key = newKey();

        assertThatThrownBy(() -> ledger.transfer(payment, key)).isInstanceOf(InsufficientFundsException.class);

        Account cash = ledger.openAccount("Cash", AccountType.ASSET, MXN);
        ledger.transfer(cash.id(), alice.id(), Money.of("30.00", MXN), "Deposit");
        TransferResult retry = ledger.transfer(payment, key);

        assertThat(retry.replayed()).isFalse();
        assertThat(ledger.balanceOf(bob.id())).isEqualTo(Money.of("30.00", MXN));
    }

    @Test
    void concurrentRetriesWithTheSameKeyPostExactlyOnce() throws Exception {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        TransferCommand payment = new TransferCommand(alice.id(), bob.id(), Money.of("10.00", MXN), "Dinner");
        String key = newKey();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 30; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return ledger.transfer(payment, key);
                }));
            }
            start.countDown();
            List<TransferResult> results = new ArrayList<>();
            for (Future<TransferResult> future : futures) {
                results.add(future.get());
            }

            assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
            assertThat(results).extracting(r -> r.entry().id()).containsOnly(results.getFirst().entry().id());
        }
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("90.00", MXN));
        assertThat(ledger.balanceOf(bob.id())).isEqualTo(Money.of("10.00", MXN));
    }

    @Test
    void transfersWithoutAKeyAreNotDeduplicated() {
        Account alice = wallet("Alice wallet", "100.00");
        Account bob = wallet("Bob wallet", null);
        TransferCommand payment = new TransferCommand(alice.id(), bob.id(), Money.of("10.00", MXN), "Coffee");

        JournalEntryId first = ledger.transfer(payment, null).entry().id();
        JournalEntryId second = ledger.transfer(payment, null).entry().id();

        assertThat(first).isNotEqualTo(second);
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.of("80.00", MXN));
    }
}
