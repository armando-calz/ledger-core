package io.github.armandocalz.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.armandocalz.ledger.PostgresTestcontainer;
import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountType;
import io.github.armandocalz.ledger.domain.InsufficientFundsException;
import io.github.armandocalz.ledger.domain.Money;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Races many transfers against the same accounts to prove balances stay correct under
 * concurrency (ADR 0004). Without row locks, {@link #neverSpendsTheSameMoneyTwice} fails:
 * several transfers read the same balance, all pass the overdraft check, and the wallet
 * ends up negative.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class LedgerConcurrencyIT {

    private static final Currency MXN = Currency.getInstance("MXN");
    private static final int THREADS = 16;

    @Autowired LedgerService ledger;

    private Account fundedWallet(String name, String amount) {
        Account cash = ledger.openAccount("Cash for " + name, AccountType.ASSET, MXN);
        Account wallet = ledger.openAccount(name, AccountType.LIABILITY, MXN);
        ledger.transfer(cash.id(), wallet.id(), Money.of(amount, MXN), "Deposit");
        return wallet;
    }

    @Test
    void neverSpendsTheSameMoneyTwice() throws Exception {
        Account alice = fundedWallet("Alice wallet", "100.00");
        Account bob = ledger.openAccount("Bob wallet", AccountType.LIABILITY, MXN);

        List<Throwable> failures = race(500, i ->
                ledger.transfer(alice.id(), bob.id(), Money.of("1.00", MXN), "Payment " + i));

        assertThat(failures).hasSize(400).allMatch(InsufficientFundsException.class::isInstance);
        assertThat(ledger.balanceOf(alice.id())).isEqualTo(Money.zero(MXN));
        assertThat(ledger.balanceOf(bob.id())).isEqualTo(Money.of("100.00", MXN));
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        Account alice = fundedWallet("Alice wallet", "1000.00");
        Account bob = fundedWallet("Bob wallet", "1000.00");

        List<Throwable> failures = race(200, i -> {
            if (i % 2 == 0) {
                ledger.transfer(alice.id(), bob.id(), Money.of("1.00", MXN), "A->B " + i);
            } else {
                ledger.transfer(bob.id(), alice.id(), Money.of("1.00", MXN), "B->A " + i);
            }
        });

        assertThat(failures).isEmpty();
        assertThat(ledger.balanceOf(alice.id()).plus(ledger.balanceOf(bob.id())))
                .as("money is conserved").isEqualTo(Money.of("2000.00", MXN));
    }

    /** Runs {@code count} tasks released at the same instant; returns the exceptions thrown. */
    private static List<Throwable> race(int count, Task task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < count; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    task.run(n);
                    return null;
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        }
    }

    @FunctionalInterface
    private interface Task {
        void run(int i) throws Exception;
    }
}
