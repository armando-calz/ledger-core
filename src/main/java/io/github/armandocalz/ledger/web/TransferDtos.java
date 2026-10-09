package io.github.armandocalz.ledger.web;

import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;
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

    record JournalEntryResponse(
            UUID id,
            String description,
            List<PostingResponse> postings,
            @Schema(description = "Set when this entry is a reversal: the entry it undoes") UUID reversesEntryId,
            @Schema(description = "Set when this entry was reversed: the reversal's id") UUID reversedByEntryId) {

        static JournalEntryResponse from(JournalEntry entry) {
            return from(entry, null);
        }

        static JournalEntryResponse from(JournalEntry entry, JournalEntryId reversedBy) {
            return new JournalEntryResponse(entry.id().value(), entry.description(),
                    entry.postings().stream().map(PostingResponse::from).toList(),
                    entry.reverses() == null ? null : entry.reverses().value(),
                    reversedBy == null ? null : reversedBy.value());
        }
    }

    record ReversalRequest(@Size(max = 500) @Schema(example = "Refund") String description) {
    }
}
