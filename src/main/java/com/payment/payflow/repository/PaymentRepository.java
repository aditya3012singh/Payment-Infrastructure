package com.payment.payflow.repository;

import com.payment.payflow.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    // Spring Data JPA will automatically implement this method based on the name!
    // We will use this to quickly check if a payment with this key already exists.
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    
    java.util.List<Payment> findByStatus(com.payment.payflow.enums.PaymentStatus status);
}
