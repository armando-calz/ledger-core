package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountMovement;
import io.github.armandocalz.ledger.domain.AccountStatus;
import io.github.armandocalz.ledger.domain.AccountType;
import io.github.armandocalz.ledger.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class AccountDtos {

    private AccountDtos() {
    }

    record OpenAccountRequest(
            @NotBlank @Size(max = 100) @Schema(example = "Alice wallet") String name,
            @NotNull @Schema(example = "LIABILITY") AccountType type,
            @NotNull @Pattern(regexp = "[A-Z]{3}") @Schema(example = "MXN", description = "ISO 4217 code") String currency,
            @Schema(description = "Let the balance go below zero. Only for internal accounts, never customer wallets.",
                    defaultValue = "false")
            Boolean allowNegativeBalance) {

        boolean negativeBalanceAllowed() {
            return Boolean.TRUE.equals(allowNegativeBalance);
        }
    }

    record AccountResponse(
            UUID id, String name, AccountType type, String currency, AccountStatus status, boolean allowNegativeBalance) {

        static AccountResponse from(Account account) {
            return new AccountResponse(account.id().value(), account.name(), account.type(),
                    account.currency().getCurrencyCode(), account.status(), account.allowNegativeBalance());
        }
    }

    @Schema(description = "Balance from the account holder's point of view")
    record BalanceResponse(UUID accountId, @Schema(example = "400.00") String amount, String currency) {

        static BalanceResponse from(UUID accountId, Money balance) {
            return new BalanceResponse(accountId, balance.toDecimal().toPlainString(), balance.currency().getCurrencyCode());
        }
    }

    record MovementResponse(
            UUID entryId,
            String description,
            @Schema(example = "-100.00", description = "Effect on the holder's balance: positive increases it")
            String amount,
            String currency,
            Instant createdAt) {

        static MovementResponse from(AccountMovement movement, AccountType type) {
            Money effect = type.presentBalance(movement.amount());
            return new MovementResponse(movement.entryId().value(), movement.description(),
                    effect.toDecimal().toPlainString(), effect.currency().getCurrencyCode(), movement.createdAt());
        }
    }
}
