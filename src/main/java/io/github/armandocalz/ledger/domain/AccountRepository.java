package io.github.armandocalz.ledger.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface AccountRepository {

    void create(Account account);

    Optional<Account> findById(AccountId id);

    /**
     * Locks the given accounts until the current transaction ends and returns their current
     * state. Locks are always taken in the same order (by id), so two transactions touching the
     * same accounts cannot deadlock. Unknown ids are absent from the result.
     */
    Map<AccountId, Account> lockAll(Collection<AccountId> ids);

    /** Persists a status change (the only mutable attribute of an account). */
    void updateStatus(Account account);
}
