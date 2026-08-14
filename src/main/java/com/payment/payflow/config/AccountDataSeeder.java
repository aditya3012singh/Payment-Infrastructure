package com.payment.payflow.config;

import com.payment.payflow.entity.Account;
import com.payment.payflow.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AccountDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountDataSeeder.class);
    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        seedAccount("USER_DEFAULT_WALLET", new BigDecimal("1000.00"));
        seedAccount("MERCHANT_DEFAULT_WALLET", BigDecimal.ZERO);
    }

    private void seedAccount(String name, BigDecimal initialBalance) {
        if (accountRepository.findByName(name).isEmpty()) {
            Account account = Account.builder()
                    .name(name)
                    .currency("USD")
                    .balance(initialBalance)
                    .build();
            accountRepository.save(account);
            log.info("Seeded account: {} with balance: {}", name, initialBalance);
        }
    }
}
