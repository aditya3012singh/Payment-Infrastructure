package com.payment.payflow.service;

import com.payment.payflow.entity.Payment;
import com.payment.payflow.enums.PaymentStatus;
import com.payment.payflow.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProviderGateway providerGateway;

    /**
     * In a real application, this would run at 2 AM every day (cron = "0 0 2 * * ?").
     * For demonstration, we run it every 30 seconds!
     */
    @Scheduled(fixedRate = 30000)
    @Transactional(readOnly = true)
    public void runDailyReconciliation() {
        log.info("🔍 RECONCILIATION ENGINE: Starting batch reconciliation...");

        // 1. Get the "Settlement Report" from Stripe (The Source of Truth)
        Set<String> externalSettledIds = providerGateway.getDailySettlementReport();

        // 2. Get all payments that our Database THINKS are completed
        List<Payment> internalCompletedPayments = paymentRepository.findByStatus(PaymentStatus.COMPLETED);

        int discrepancies = 0;

        for (Payment internalPayment : internalCompletedPayments) {
            String paymentId = internalPayment.getId().toString();
            
            // Check if our completed payment is missing from Stripe's settlement report!
            if (!externalSettledIds.contains(paymentId)) {
                log.error("🚨 RECONCILIATION ALERT: Payment {} is marked COMPLETED in our database but is MISSING from the external provider's settlement report! We might have lost money!", paymentId);
                discrepancies++;
            }
        }

        if (discrepancies == 0) {
            log.info("✅ RECONCILIATION ENGINE: All {} internal payments perfectly match the external provider. No discrepancies found.", internalCompletedPayments.size());
        } else {
            log.error("❌ RECONCILIATION ENGINE: Found {} discrepancies! Immediate accountant review required.", discrepancies);
        }
    }
}
