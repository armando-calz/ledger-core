package io.github.armandocalz.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.Money;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class TransferCommandTest {

    private static final Currency MXN = Currency.getInstance("MXN");
    private final AccountId alice = AccountId.random();
    private final AccountId bob = AccountId.random();

    @Test
    void sameRequestHasSameFingerprint() {
        assertThat(new TransferCommand(alice, bob, Money.of("10.00", MXN), "Dinner").fingerprint())
                .isEqualTo(new TransferCommand(alice, bob, Money.of("10.0", MXN), " Dinner ").fingerprint());
    }

    @Test
    void anyDifferentFieldChangesTheFingerprint() {
        String original = new TransferCommand(alice, bob, Money.of("10.00", MXN), "Dinner").fingerprint();

        assertThat(new TransferCommand(bob, alice, Money.of("10.00", MXN), "Dinner").fingerprint()).isNotEqualTo(original);
        assertThat(new TransferCommand(alice, bob, Money.of("10.01", MXN), "Dinner").fingerprint()).isNotEqualTo(original);
        assertThat(new TransferCommand(alice, bob, Money.of("10.00", Currency.getInstance("USD")), "Dinner").fingerprint())
                .isNotEqualTo(original);
        assertThat(new TransferCommand(alice, bob, Money.of("10.00", MXN), "Lunch").fingerprint()).isNotEqualTo(original);
    }

    @Test
    void fieldsCannotBleedIntoEachOther() {
        // Without length prefixes, "a|b" + "c" and "a" + "b|c" would hash the same input
        assertThat(new TransferCommand(alice, bob, Money.of("1.00", MXN), "x|1").fingerprint())
                .isNotEqualTo(new TransferCommand(alice, bob, Money.of("1.00", MXN), "x").fingerprint());
    }
}
