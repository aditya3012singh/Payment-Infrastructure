package com.payment.payflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderGateway.class);
    private final Random random = new Random();

    private final Set<String> settledPayments = ConcurrentHashMap.newKeySet();

    /**
     * Simulates sending a payment request to a 3rd-party provider (like Stripe).
     * This method intentionally fails ~30% of the time to demonstrate Kafka retries!
     */
    public boolean processWithProvider(String paymentId, java.math.BigDecimal amount) {
        log.info("GATEWAY: Sending payment {} for ${} to external provider...", paymentId, amount);
        
        // Simulate network delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 30% failure rate
        if (random.nextInt(100) < 30) {
            log.error("GATEWAY: HTTP 503 Service Unavailable from external provider for payment {}!", paymentId);
            throw new RuntimeException("External Provider is down!");
        }

        log.info("GATEWAY: HTTP 200 OK. Payment {} successfully processed by external provider.", paymentId);
        settledPayments.add(paymentId);
        return true;
    }

    /**
     * Simulates fetching a "Settlement Report" from Stripe/Bank.
     * In real life, this would be parsing a CSV file sent by the bank via SFTP.
     */
    public Set<String> getDailySettlementReport() {
        return new HashSet<>(settledPayments);
    }
}
