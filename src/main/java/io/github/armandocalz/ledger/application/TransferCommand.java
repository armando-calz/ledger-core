package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** A request to debit one account and credit another. */
public record TransferCommand(AccountId debitAccount, AccountId creditAccount, Money amount, String description) {

    public TransferCommand {
        Objects.requireNonNull(debitAccount, "debitAccount");
        Objects.requireNonNull(creditAccount, "creditAccount");
        Objects.requireNonNull(amount, "amount");
        description = description == null ? "" : description.strip();
    }

    /**
     * SHA-256 of every field, used to detect an idempotency key reused for a different request.
     * Fields are length-prefixed so that no two different commands produce the same input.
     */
    public String fingerprint() {
        String canonical = String.join("|",
                field(debitAccount.toString()),
                field(creditAccount.toString()),
                field(Long.toString(amount.amountMinor())),
                field(amount.currency().getCurrencyCode()),
                field(description));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String field(String value) {
        return value.length() + ":" + value;
    }
}
