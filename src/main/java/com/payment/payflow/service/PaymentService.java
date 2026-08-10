package com.payment.payflow.service;

import com.payment.payflow.entity.Payment;
import com.payment.payflow.enums.PaymentStatus;
import com.payment.payflow.exception.PaymentProcessingException;
import com.payment.payflow.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    
    private final PaymentRepository paymentRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "payment:idempotency:";

    /**
     * Processes a new payment with strict idempotency guarantees.
     */
    @Transactional
    public Payment processPayment(String idempotencyKey, BigDecimal amount, String currency) {
        
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // 1. FAST IDEMPOTENCY CHECK (Redis SETNX)
        // Try to set the key in Redis with a 24-hour expiration. 
        // If it already exists, this returns false immediately.
        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));
        
        if (Boolean.FALSE.equals(isNewRequest)) {
            log.warn("Duplicate request detected for idempotency key: {}", idempotencyKey);
            
            // If it's a duplicate, we should check if it's already in the DB to return the final state.
            // If it's not in the DB yet, it means another thread is currently saving it.
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PaymentProcessingException(
                            "Payment is currently processing. Please try again later.", "PAYMENT_IN_PROGRESS"));
        }

        try {
            log.info("Processing new payment with key: {}", idempotencyKey);
            
            // 2. Create the Payment Entity
            Payment payment = Payment.builder()
                    .idempotencyKey(idempotencyKey)
                    .amount(amount)
                    .currency(currency)
                    .status(PaymentStatus.PENDING)
                    .build();
            
            // 3. Save to PostgreSQL
            payment = paymentRepository.save(payment);
            
            // 4. Update Redis to indicate completion (optional, but good practice)
            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofHours(24));
            
            log.info("Successfully created payment with ID: {}", payment.getId());
            return payment;

        } catch (Exception e) {
            // If the database transaction fails (e.g. database goes down), 
            // we MUST remove the idempotency key from Redis so the user can safely retry!
            redisTemplate.delete(redisKey);
            log.error("Failed to process payment. Removed idempotency key from Redis.", e);
            throw new PaymentProcessingException("Internal error processing payment", "INTERNAL_ERROR");
        }
    }
}
