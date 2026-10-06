package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.Posting;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

final class TransferDtos {

    private TransferDtos() {
    }

    record TransferRequest(
            @NotNull UUID debitAccountId,
            @NotNull UUID creditAccountId,
            @NotNull @Pattern(regexp = "\\d{1,15}(\\.\\d{1,4})?")
            @Schema(example = "100.00", description = "Positive decimal as a string, never a float")
            String amount,
            @NotNull @Pattern(regexp = "[A-Z]{3}") @Schema(example = "MXN") String currency,
            @Size(max = 500) @Schema(example = "Dinner") String description) {
    }

    record PostingResponse(
            UUID accountId,
            @Schema(example = "100.00", description = "Positive = debit, negative = credit") String amount,
            String currency) {

        static PostingResponse from(Posting posting) {
            return new PostingResponse(posting.accountId().value(),
                    posting.amount().toDecimal().toPlainString(), posting.amount().currency().getCurrencyCode());
        }
    }

    record JournalEntryResponse(UUID id, String description, List<PostingResponse> postings) {

        static JournalEntryResponse from(JournalEntry entry) {
            return new JournalEntryResponse(entry.id().value(), entry.description(),
                    entry.postings().stream().map(PostingResponse::from).toList());
        }
    }
}
