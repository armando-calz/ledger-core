package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.application.LedgerService;
import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.web.AccountDtos.AccountResponse;
import io.github.armandocalz.ledger.web.AccountDtos.BalanceResponse;
import io.github.armandocalz.ledger.web.AccountDtos.MovementResponse;
import io.github.armandocalz.ledger.web.AccountDtos.OpenAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
class AccountController {

    private final LedgerService ledger;

    AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @Operation(summary = "Open an account")
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = ledger.openAccount(request.name(), request.type(), Currency.getInstance(request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(ledger.getAccount(new AccountId(id)));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get the balance, from the account holder's point of view")
    BalanceResponse balance(@PathVariable UUID id) {
        return BalanceResponse.from(id, ledger.balanceOf(new AccountId(id)));
    }

    @GetMapping("/{id}/movements")
    @Operation(summary = "List the most recent movements, newest first")
    List<MovementResponse> movements(@PathVariable UUID id, @RequestParam(defaultValue = "20") int limit) {
        Account account = ledger.getAccount(new AccountId(id));
        return ledger.movementsOf(account.id(), limit).stream()
                .map(movement -> MovementResponse.from(movement, account.type()))
                .toList();
    }
}
