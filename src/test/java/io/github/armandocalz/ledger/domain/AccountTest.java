package io.github.armandocalz.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountTest {

    private static final Currency MXN = Currency.getInstance("MXN");
    private static final Currency USD = Currency.getInstance("USD");

    private final Account wallet = Account.open(AccountId.random(), "Alice wallet", AccountType.LIABILITY, MXN);

    @Nested
    class AcceptingPostings {

        @Test
        void acceptsPostingsInItsCurrency() {
            wallet.ensureAccepts(Posting.credit(wallet.id(), Money.of("10.00", MXN)));
        }

        @Test
        void rejectsPostingsInAnotherCurrency() {
            assertThatThrownBy(() -> wallet.ensureAccepts(Posting.credit(wallet.id(), Money.of("10.00", USD))))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        void rejectsPostingsForAnotherAccount() {
            assertThatThrownBy(() -> wallet.ensureAccepts(Posting.credit(AccountId.random(), Money.of("10.00", MXN))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targets");
        }

        @Test
        void rejectsPostingsWhenFrozenOrClosed() {
            Posting posting = Posting.credit(wallet.id(), Money.of("10.00", MXN));

            assertThatThrownBy(() -> wallet.freeze().ensureAccepts(posting)).isInstanceOf(AccountNotActiveException.class);
            assertThatThrownBy(() -> wallet.close().ensureAccepts(posting)).isInstanceOf(AccountNotActiveException.class);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void opensActive() {
            assertThat(wallet.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void canBeFrozenAndUnfrozen() {
            Account frozen = wallet.freeze();

            assertThat(frozen.status()).isEqualTo(AccountStatus.FROZEN);
            assertThat(frozen.unfreeze().status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(wallet.status()).as("original is unchanged").isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void rejectsInvalidTransitions() {
            assertThatThrownBy(wallet::unfreeze).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> wallet.freeze().freeze()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void closedIsTerminal() {
            Account closed = wallet.close();

            assertThatThrownBy(closed::freeze).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(closed::unfreeze).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(closed::close).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void requiresAName() {
            assertThatThrownBy(() -> Account.open(AccountId.random(), "  ", AccountType.ASSET, MXN))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Overdraft {

        private final Money oneHundred = Money.of("100.00", MXN);

        @Test
        void allowsSpendingTheWholeBalance() {
            // Wallet holding 100.00 (raw -100.00) is debited 100.00
            wallet.ensureCanApply(oneHundred.negate(), oneHundred);
        }

        @Test
        void rejectsSpendingMoreThanTheBalance() {
            assertThatThrownBy(() -> wallet.ensureCanApply(oneHundred.negate(), Money.of("100.01", MXN)))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("available 100.00 MXN")
                    .hasMessageContaining("requested 100.01 MXN");
        }

        @Test
        void alwaysAllowsIncreasingTheBalance() {
            wallet.ensureCanApply(Money.zero(MXN), oneHundred.negate());
        }

        @Test
        void appliesToEveryAccountTypeFromTheHoldersPointOfView() {
            Account cash = Account.open(AccountId.random(), "Cash", AccountType.ASSET, MXN);

            cash.ensureCanApply(oneHundred, oneHundred.negate());
            assertThatThrownBy(() -> cash.ensureCanApply(oneHundred, Money.of("-100.01", MXN)))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void canBeAllowedExplicitly() {
            Account settlement = Account.open(AccountId.random(), "Settlement", AccountType.ASSET, MXN, true);

            settlement.ensureCanApply(Money.zero(MXN), Money.of("-1000.00", MXN));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ASSET,     -2500, -2500",
            "EXPENSE,    2500,  2500",
            "LIABILITY, -2500,  2500",
            "EQUITY,    -2500,  2500",
            "REVENUE,    2500, -2500",
    })
    void presentsBalanceFromTheHoldersPointOfView(AccountType type, long rawMinor, long presentedMinor) {
        assertThat(type.presentBalance(Money.ofMinor(rawMinor, MXN))).isEqualTo(Money.ofMinor(presentedMinor, MXN));
    }
}
