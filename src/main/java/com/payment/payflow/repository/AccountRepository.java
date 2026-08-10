package com.payment.payflow.repository;

import com.payment.payflow.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    
    // Pessimistic Write Lock ensures no two threads can update this account's balance simultaneously.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findByName(String name);
}
