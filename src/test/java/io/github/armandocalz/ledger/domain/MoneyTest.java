package io.github.armandocalz.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    private static final Currency MXN = Currency.getInstance("MXN");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Nested
    class Parsing {

        @ParameterizedTest
        @CsvSource({
                "100.50, MXN, 10050",
                "100.5,  MXN, 10050",
                "100,    MXN, 10000",
                "0.01,   MXN, 1",
                "-25.10, MXN, -2510",
                "100.500, MXN, 10050",
                "1500,   JPY, 1500",
        })
        void convertsDecimalToMinorUnits(String amount, String currency, long expectedMinor) {
            Money money = Money.of(amount, Currency.getInstance(currency));

            assertThat(money.amountMinor()).isEqualTo(expectedMinor);
        }

        @ParameterizedTest
        @CsvSource({"100.505, MXN", "0.001, USD", "100.5, JPY"})
        void rejectsMorePrecisionThanTheCurrencyAllows(String amount, String currency) {
            assertThatThrownBy(() -> Money.of(amount, Currency.getInstance(currency)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decimal places");
        }

        @Test
        void rejectsAmountsThatDoNotFitInMinorUnits() {
            assertThatThrownBy(() -> Money.of("99999999999999999999", MXN))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void rejectsCurrenciesWithoutMinorUnit() {
            assertThatThrownBy(() -> Money.zero(Currency.getInstance("XXX")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void isExactWhereFloatingPointIsNot() {
            Money sum = Money.of("0.10", USD).plus(Money.of("0.20", USD));

            assertThat(sum).isEqualTo(Money.of("0.30", USD));
        }

        @Test
        void addsSubtractsAndNegates() {
            Money a = Money.ofMinor(10050, MXN);
            Money b = Money.ofMinor(2525, MXN);

            assertThat(a.plus(b)).isEqualTo(Money.ofMinor(12575, MXN));
            assertThat(a.minus(b)).isEqualTo(Money.ofMinor(7525, MXN));
            assertThat(b.negate()).isEqualTo(Money.ofMinor(-2525, MXN));
        }

        @Test
        void refusesToMixCurrencies() {
            assertThatThrownBy(() -> Money.ofMinor(100, MXN).plus(Money.ofMinor(100, USD)))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("MXN")
                    .hasMessageContaining("USD");
        }

        @Test
        void failsOnOverflowInsteadOfWrappingAround() {
            Money max = Money.ofMinor(Long.MAX_VALUE, MXN);

            assertThatThrownBy(() -> max.plus(Money.ofMinor(1, MXN))).isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(() -> Money.ofMinor(Long.MIN_VALUE, MXN).negate()).isInstanceOf(ArithmeticException.class);
        }
    }

    @Nested
    class Presentation {

        @Test
        void formatsWithTheCurrencyScale() {
            assertThat(Money.ofMinor(10050, MXN)).hasToString("100.50 MXN");
            assertThat(Money.ofMinor(-5, MXN)).hasToString("-0.05 MXN");
            assertThat(Money.ofMinor(1500, JPY)).hasToString("1500 JPY");
        }

        @Test
        void exposesDecimalValue() {
            assertThat(Money.ofMinor(10050, MXN).toDecimal()).isEqualByComparingTo(new BigDecimal("100.50"));
        }

        @Test
        void reportsSign() {
            assertThat(Money.zero(MXN).isZero()).isTrue();
            assertThat(Money.ofMinor(1, MXN).isPositive()).isTrue();
            assertThat(Money.ofMinor(-1, MXN).isNegative()).isTrue();
        }
    }
}
