package io.github.armandocalz.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Currency MXN = Currency.getInstance("MXN");
    private static final Currency USD = Currency.getInstance("USD");

    private final AccountId alice = AccountId.random();
    private final AccountId bob = AccountId.random();
    private final AccountId fees = AccountId.random();
    private final AccountId fxPool = AccountId.random();

    private static Money mxn(String amount) {
        return Money.of(amount, MXN);
    }

    @Nested
    class Transfers {

        @Test
        void debitsOneAccountAndCreditsTheOtherBySameAmount() {
            // Alice pays Bob: both are customer wallets (liabilities)
            JournalEntry entry = JournalEntry.transfer(JournalEntryId.random(), alice, bob, mxn("100.00"), "Dinner");

            assertThat(entry.postings()).containsExactly(
                    new Posting(alice, mxn("100.00")),
                    new Posting(bob, mxn("-100.00")));
            assertThat(entry.description()).isEqualTo("Dinner");
        }

        @Test
        void rejectsTransfersToTheSameAccount() {
            assertThatThrownBy(() -> JournalEntry.transfer(JournalEntryId.random(), alice, alice, mxn("1.00"), ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same account");
        }

        @Test
        void rejectsNonPositiveAmounts() {
            assertThatThrownBy(() -> JournalEntry.transfer(JournalEntryId.random(), alice, bob, mxn("-5.00"), ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
            assertThatThrownBy(() -> JournalEntry.transfer(JournalEntryId.random(), alice, bob, mxn("0"), ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Balancing {

        @Test
        void acceptsMultiLegEntriesThatSumToZero() {
            // Alice pays 100.00 to Bob plus a 1.00 fee
            JournalEntry entry = new JournalEntry(JournalEntryId.random(), "Transfer with fee", List.of(
                    Posting.credit(alice, mxn("101.00")),
                    Posting.debit(bob, mxn("100.00")),
                    Posting.debit(fees, mxn("1.00"))));

            assertThat(entry.postings()).hasSize(3);
        }

        @Test
        void balancesEachCurrencyIndependently() {
            // Alice converts 10.00 USD into 180.00 MXN for Bob through an FX pool
            JournalEntry entry = new JournalEntry(JournalEntryId.random(), "FX transfer", List.of(
                    Posting.credit(alice, Money.of("10.00", USD)),
                    Posting.debit(fxPool, Money.of("10.00", USD)),
                    Posting.credit(fxPool, mxn("180.00")),
                    Posting.debit(bob, mxn("180.00"))));

            assertThat(entry.postings()).hasSize(4);
        }

        @Test
        void rejectsEntriesThatCreateMoney() {
            assertThatThrownBy(() -> new JournalEntry(JournalEntryId.random(), "", List.of(
                    Posting.credit(alice, mxn("100.00")),
                    Posting.debit(bob, mxn("100.01")))))
                    .isInstanceOf(UnbalancedEntryException.class)
                    .hasMessageContaining("0.01 MXN");
        }

        @Test
        void rejectsEntriesBalancedInTotalButNotPerCurrency() {
            // 100 minor units of USD do not cancel 100 minor units of MXN
            assertThatThrownBy(() -> new JournalEntry(JournalEntryId.random(), "", List.of(
                    Posting.credit(alice, Money.ofMinor(100, USD)),
                    Posting.debit(bob, Money.ofMinor(100, MXN)))))
                    .isInstanceOf(UnbalancedEntryException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("MXN");
        }

        @Test
        void requiresAtLeastTwoPostings() {
            assertThatThrownBy(() -> new JournalEntry(JournalEntryId.random(), "", List.of(Posting.debit(bob, mxn("1.00")))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2");
        }

        @Test
        void rejectsZeroAmountPostings() {
            assertThatThrownBy(() -> new Posting(alice, Money.zero(MXN)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void detectsOverflowWhileSumming() {
            assertThatThrownBy(() -> new JournalEntry(JournalEntryId.random(), "", List.of(
                    Posting.debit(alice, Money.ofMinor(Long.MAX_VALUE, MXN)),
                    Posting.debit(bob, Money.ofMinor(1, MXN)),
                    Posting.credit(fees, Money.ofMinor(1, MXN)))))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    @Test
    void isImmutable() {
        List<Posting> postings = new ArrayList<>(List.of(
                Posting.credit(alice, mxn("5.00")),
                Posting.debit(bob, mxn("5.00"))));
        JournalEntry entry = new JournalEntry(JournalEntryId.random(), "", postings);

        postings.add(Posting.debit(fees, mxn("1000.00")));

        assertThat(entry.postings()).hasSize(2);
        assertThatThrownBy(() -> entry.postings().add(Posting.debit(fees, mxn("1.00"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
