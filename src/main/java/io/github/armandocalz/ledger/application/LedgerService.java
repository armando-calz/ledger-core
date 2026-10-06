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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the ledger. Each public method is one transaction. */
@Service
public class LedgerService {

    public static final int MAX_MOVEMENTS = 100;

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    public LedgerService(AccountRepository accounts, JournalEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional
    public Account openAccount(String name, AccountType type, Currency currency) {
        Account account = Account.open(AccountId.random(), name, type, currency);
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
        return post(JournalEntry.transfer(JournalEntryId.random(), debitAccount, creditAccount, amount, description));
    }

    /** Validates every posting against its account, then appends the entry. */
    @Transactional
    public JournalEntry post(JournalEntry entry) {
        for (Posting posting : entry.postings()) {
            getAccount(posting.accountId()).ensureAccepts(posting);
        }
        entries.append(entry);
        return entry;
    }
}
