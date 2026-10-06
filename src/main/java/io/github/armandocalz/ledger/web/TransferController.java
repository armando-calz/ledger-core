package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.application.LedgerService;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.Money;
import io.github.armandocalz.ledger.web.TransferDtos.JournalEntryResponse;
import io.github.armandocalz.ledger.web.TransferDtos.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Currency;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
class TransferController {

    private final LedgerService ledger;

    TransferController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Debit one account and credit another by the same amount",
            description = "Between two customer wallets (liabilities), a payment from Alice to Bob debits Alice "
                    + "and credits Bob. See ADR 0002.")
    JournalEntryResponse transfer(@Valid @RequestBody TransferRequest request) {
        Money amount = Money.of(request.amount(), Currency.getInstance(request.currency()));
        JournalEntry entry = ledger.transfer(
                new AccountId(request.debitAccountId()), new AccountId(request.creditAccountId()),
                amount, request.description());
        return JournalEntryResponse.from(entry);
    }
}
