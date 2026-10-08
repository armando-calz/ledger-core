package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.application.LedgerService;
import io.github.armandocalz.ledger.application.TransferCommand;
import io.github.armandocalz.ledger.application.TransferResult;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.Money;
import io.github.armandocalz.ledger.web.TransferDtos.JournalEntryResponse;
import io.github.armandocalz.ledger.web.TransferDtos.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Currency;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final LedgerService ledger;

    TransferController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @Operation(summary = "Debit one account and credit another by the same amount",
            description = "Between two customer wallets (liabilities), a payment from Alice to Bob debits Alice "
                    + "and credits Bob. See ADR 0002. Send an Idempotency-Key to make retries safe (ADR 0005).")
    ResponseEntity<JournalEntryResponse> transfer(
            @Parameter(description = "Unique key per logical transfer; a retry with the same key and body "
                    + "returns the original result without moving money again")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(min = 1, max = 255) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        Money amount = Money.of(request.amount(), Currency.getInstance(request.currency()));
        TransferCommand command = new TransferCommand(
                new AccountId(request.debitAccountId()), new AccountId(request.creditAccountId()),
                amount, request.description());
        TransferResult result = ledger.transfer(command, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()))
                .body(JournalEntryResponse.from(result.entry()));
    }
}
