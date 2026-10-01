package io.github.armandocalz.ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record JournalEntryId(UUID value) {

    public JournalEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static JournalEntryId random() {
        return new JournalEntryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
