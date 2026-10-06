package io.github.armandocalz.ledger.domain;

import java.util.Optional;

public interface AccountRepository {

    void create(Account account);

    Optional<Account> findById(AccountId id);

    /** Persists a status change (the only mutable attribute of an account). */
    void updateStatus(Account account);
}
