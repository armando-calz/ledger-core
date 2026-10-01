package io.github.armandocalz.ledger.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact monetary amount, stored as a signed count of the currency's minor unit
 * (e.g. {@code 10050} MXN = {@code 100.50 MXN}). See ADR 0001.
 *
 * <p>Negative amounts are valid: postings use the sign to tell debits from credits.
 * All arithmetic fails fast on overflow instead of wrapping around.
 */
public record Money(long amountMinor, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Currency has no minor unit: " + currency);
        }
    }

    public static Money ofMinor(long amountMinor, Currency currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /**
     * Parses a decimal amount such as {@code "100.50"}.
     *
     * @throws IllegalArgumentException if the amount has more decimals than the currency allows
     * @throws ArithmeticException      if the amount does not fit in a {@code long} of minor units
     */
    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        return of(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int fractionDigits = currency.getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > fractionDigits) {
            throw new IllegalArgumentException(
                    "%s allows at most %d decimal places, got %s".formatted(currency, fractionDigits, amount.toPlainString()));
        }
        return new Money(amount.movePointRight(fractionDigits).longValueExact(), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits());
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
