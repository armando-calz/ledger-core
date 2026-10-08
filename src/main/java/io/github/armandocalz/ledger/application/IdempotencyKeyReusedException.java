package io.github.armandocalz.ledger.application;

/** Thrown when an idempotency key is sent again with a different request. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency key '%s' was already used for a different request".formatted(key));
    }
}
