package com.payment.payflow.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payment.payflow.entity.Account;
import com.payment.payflow.entity.LedgerEntry;
import com.payment.payflow.entity.LedgerTransaction;
import com.payment.payflow.enums.EntryDirection;
import com.payment.payflow.exception.PaymentProcessingException;
import com.payment.payflow.repository.AccountRepository;
import com.payment.payflow.repository.LedgerTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    /**
     * Records a new double-entry ledger transaction.
     * This method is entirely atomic. If anything fails, nothing is saved.
     */
    @Transactional
    public LedgerTransaction recordTransaction(String idempotencyKey, String description, List<LedgerEntry> entries) {
        
        log.info("Attempting to record ledger transaction: {}", description);

        // 1. Verify Double-Entry Accounting Rule (Debits == Credits)
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (LedgerEntry entry : entries) {
            if (entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentProcessingException("Ledger entry amount must be strictly positive", "INVALID_AMOUNT");
            }

            if (entry.getDirection() == EntryDirection.DEBIT) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else if (entry.getDirection() == EntryDirection.CREDIT) {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new PaymentProcessingException(
                    "Ledger imbalance detected! Debits (" + totalDebits + ") do not equal Credits (" + totalCredits + ").", 
                    "LEDGER_IMBALANCE");
        }

        // 2. Create the overarching Transaction Envelope
        LedgerTransaction transaction = LedgerTransaction.builder()
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();

        // 3. Process entries and update account balances securely
        for (LedgerEntry entry : entries) {
            entry.setTransaction(transaction); // Link back to parent

            // We must fetch the account with a PESSIMISTIC_WRITE lock. 
            // This prevents race conditions if 100 people try to pay the same merchant simultaneously.
            Account account = accountRepository.findByNameForUpdate(entry.getAccount().getName())
                    .orElseThrow(() -> new PaymentProcessingException("Account not found: " + entry.getAccount().getName(), "ACCOUNT_NOT_FOUND"));

            // Update balance
            if (entry.getDirection() == EntryDirection.CREDIT) {
                account.setBalance(account.getBalance().add(entry.getAmount()));
            } else {
                account.setBalance(account.getBalance().subtract(entry.getAmount()));
                // Optionally: Check for insufficient funds if business logic requires it
                // if (account.getBalance().compareTo(BigDecimal.ZERO) < 0) { throw new Exception("NSF"); }
            }

            // Save the updated account balance
            accountRepository.save(account);
            
            // Overwrite the transient account with the managed one
            entry.setAccount(account);
            transaction.getEntries().add(entry);
        }

        // 4. Save the transaction (which cascades and saves all the entries!)
        LedgerTransaction savedTx = transactionRepository.save(transaction);
        log.info("Successfully recorded ledger transaction {} with {} entries.", savedTx.getId(), entries.size());
        
        return savedTx;
    }
}
