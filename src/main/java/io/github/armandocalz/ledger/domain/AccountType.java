package io.github.armandocalz.ledger.domain;

/**
 * The five classic account types. Each has a <em>normal balance side</em>: the side
 * (debit or credit) that increases it. See ADR 0002.
 *
 * <p>Customer wallets are {@link #LIABILITY} accounts: the money in them is owed by the
 * business to its customers. The cash backing them sits in {@link #ASSET} accounts.
 */
public enum AccountType {
    ASSET(Side.DEBIT),
    EXPENSE(Side.DEBIT),
    LIABILITY(Side.CREDIT),
    EQUITY(Side.CREDIT),
    REVENUE(Side.CREDIT);

    public enum Side { DEBIT, CREDIT }

    private final Side normalSide;

    AccountType(Side normalSide) {
        this.normalSide = normalSide;
    }

    public Side normalSide() {
        return normalSide;
    }

    /**
     * Converts the raw ledger balance (sum of postings, debits positive) into the balance
     * as the account holder understands it, e.g. a customer wallet with raw balance
     * {@code -100.00} holds {@code 100.00}.
     */
    public Money presentBalance(Money rawBalance) {
        return normalSide == Side.DEBIT ? rawBalance : rawBalance.negate();
    }
}
