package com.payment.payflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payflow.entity.Payment;
import com.payment.payflow.enums.PaymentStatus;
import com.payment.payflow.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentProviderGateway providerGateway;
    private final PaymentRepository paymentRepository;
    private final StringRedisTemplate redisTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final String CONSUMER_IDEMPOTENCY_PREFIX = "payment:consumer:processed:";

    /**
     * Listens to the outbox topic. 
     * If an exception is thrown, @RetryableTopic automatically sends the message to a retry topic 
     * (e.g., payment.events-retry-1000, payment.events-retry-2000) with exponential backoff.
     * After 4 attempts, it sends it to the Dead Letter Topic (DLT).
     */
    @RetryableTopic(
            attempts = "4",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "payment.events", groupId = "payment-workers")
    @Transactional
    public void consumePaymentEvent(String payload, @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId) throws Exception {
        log.info("CONSUMER: Received event for Payment ID: {}", aggregateId);

        // 1. Idempotent Consumer Pattern (Prevent double-processing if Kafka sends duplicates)
        String redisKey = CONSUMER_IDEMPOTENCY_PREFIX + aggregateId;
        Boolean isNewProcessing = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));
        
        if (Boolean.FALSE.equals(isNewProcessing)) {
            log.warn("CONSUMER: Event for Payment {} is already being processed or completed. Skipping.", aggregateId);
            return;
        }

        try {
            // 2. Parse the JSON payload from the OutboxEvent
            JsonNode paymentJson = objectMapper.readTree(payload);
            java.math.BigDecimal amount = new java.math.BigDecimal(paymentJson.get("amount").asText());

            // 3. Simulate calling an external 3rd-party provider (e.g., Stripe)
            // This will randomly throw an exception ~30% of the time to trigger Kafka retries!
            providerGateway.processWithProvider(aggregateId, amount);

            // 4. On Success: Update the Payment status in our DB
            Payment payment = paymentRepository.findById(java.util.UUID.fromString(aggregateId))
                    .orElseThrow(() -> new RuntimeException("Payment not found in DB"));
            
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            
            // Mark as completely processed in Redis so we never process it again
            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofDays(7));
            
        } catch (Exception e) {
            // If it fails, we delete the Redis key so the retry mechanism can attempt it again!
            redisTemplate.delete(redisKey);
            log.warn("CONSUMER: Processing failed for Payment {}. Kafka will automatically retry.", aggregateId);
            throw e; // Throwing the exception tells Kafka/Spring to retry
        }
    }

    /**
     * This method handles messages that have exhausted all 4 retry attempts.
     * It is the "Dead Letter Queue" handler.
     */
    @DltHandler
    @Transactional
    public void handleDltPayment(String payload, @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId) {
        log.error("DLQ (Dead Letter Queue): Payment {} failed after all retries! Marking as FAILED.", aggregateId);
        
        try {
            Payment payment = paymentRepository.findById(java.util.UUID.fromString(aggregateId)).orElse(null);
            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        } catch (Exception e) {
            log.error("DLQ: Failed to update DB status for Payment {}", aggregateId, e);
        }
    }
}
