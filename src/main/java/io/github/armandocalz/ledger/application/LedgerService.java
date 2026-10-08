package io.github.armandocalz.ledger.application;

import io.github.armandocalz.ledger.domain.Account;
import io.github.armandocalz.ledger.domain.AccountId;
import io.github.armandocalz.ledger.domain.AccountMovement;
import io.github.armandocalz.ledger.domain.AccountRepository;
import io.github.armandocalz.ledger.domain.AccountType;
import io.github.armandocalz.ledger.domain.JournalEntry;
import io.github.armandocalz.ledger.domain.JournalEntryId;
import io.github.armandocalz.ledger.domain.JournalEntryRepository;
import io.github.armandocalz.ledger.domain.Money;
import io.github.armandocalz.ledger.domain.Posting;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the ledger. Each public method is one transaction. */
@Service
public class LedgerService {

    public static final int MAX_MOVEMENTS = 100;

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdempotencyKeyRepository idempotencyKeys;

    public LedgerService(AccountRepository accounts, JournalEntryRepository entries, IdempotencyKeyRepository idempotencyKeys) {
        this.accounts = accounts;
        this.entries = entries;
        this.idempotencyKeys = idempotencyKeys;
    }

    @Transactional
    public Account openAccount(String name, AccountType type, Currency currency) {
        return openAccount(name, type, currency, false);
    }

    @Transactional
    public Account openAccount(String name, AccountType type, Currency currency, boolean allowNegativeBalance) {
        Account account = Account.open(AccountId.random(), name, type, currency, allowNegativeBalance);
        accounts.create(account);
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    /** Balance from the account holder's point of view (ADR 0002). */
    @Transactional(readOnly = true)
    public Money balanceOf(AccountId id) {
        Account account = getAccount(id);
        return account.type().presentBalance(entries.rawBalanceOf(account));
    }

    @Transactional(readOnly = true)
    public List<AccountMovement> movementsOf(AccountId id, int limit) {
        Account account = getAccount(id);
        int boundedLimit = Math.clamp(limit, 1, MAX_MOVEMENTS);
        return entries.movementsOf(account, boundedLimit);
    }

    @Transactional
    public JournalEntry transfer(AccountId debitAccount, AccountId creditAccount, Money amount, String description) {
        return transfer(new TransferCommand(debitAccount, creditAccount, amount, description), null).entry();
    }

    /**
     * Posts a transfer at most once per idempotency key (ADR 0005).
     *
     * <p>The key is claimed in the same transaction as the entry. A retry after success returns
     * the original entry; a retry after a failure (e.g. insufficient funds) runs again, because
     * the failed transaction rolled back its claim.
     *
     * @param idempotencyKey optional; {@code null} disables idempotency
     * @throws IdempotencyKeyReusedException if the key was used for a different request
     */
    @Transactional
    public TransferResult transfer(TransferCommand command, String idempotencyKey) {
        if (idempotencyKey != null) {
            String fingerprint = command.fingerprint();
            if (!idempotencyKeys.tryClaim(idempotencyKey, fingerprint)) {
                return replay(idempotencyKey, fingerprint);
            }
        }
        JournalEntry entry = post(JournalEntry.transfer(JournalEntryId.random(),
                command.debitAccount(), command.creditAccount(), command.amount(), command.description()));
        if (idempotencyKey != null) {
            idempotencyKeys.complete(idempotencyKey, entry.id());
        }
        return new TransferResult(entry, false);
    }

    private TransferResult replay(String idempotencyKey, String fingerprint) {
        IdempotencyKeyRepository.StoredKey stored = idempotencyKeys.find(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Claimed idempotency key not found: " + idempotencyKey));
        if (!stored.requestHash().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        JournalEntry original = entries.findById(stored.journalEntryId())
                .orElseThrow(() -> new IllegalStateException("Entry not found for idempotency key: " + idempotencyKey));
        return new TransferResult(original, true);
    }

    /**
     * Validates every posting against its account (currency, status, overdraft), then appends
     * the entry.
     *
     * <p>All involved accounts are locked first, so the balances and statuses checked here
     * cannot change before the entry is committed (ADR 0004).
     */
    @Transactional
    public JournalEntry post(JournalEntry entry) {
        Set<AccountId> ids = entry.postings().stream().map(Posting::accountId).collect(Collectors.toSet());
        Map<AccountId, Account> locked = accounts.lockAll(ids);
        for (Posting posting : entry.postings()) {
            Account account = locked.get(posting.accountId());
            if (account == null) {
                throw new AccountNotFoundException(posting.accountId());
            }
            account.ensureAccepts(posting);
        }
        netChangePerAccount(entry).forEach((id, delta) -> {
            Account account = locked.get(id);
            account.ensureCanApply(entries.rawBalanceOf(account), delta);
        });
        entries.append(entry);
        return entry;
    }

    private static Map<AccountId, Money> netChangePerAccount(JournalEntry entry) {
        Map<AccountId, Money> deltas = new HashMap<>();
        for (Posting posting : entry.postings()) {
            deltas.merge(posting.accountId(), posting.amount(), Money::plus);
        }
        return deltas;
    }
}
