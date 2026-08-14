package com.payment.payflow.service;

import com.payment.payflow.entity.OutboxEvent;
import com.payment.payflow.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxPollerService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerService.class);
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    private static final String TOPIC_NAME = "payment.events";

    /**
     * Poll the database every 5 seconds for PENDING outbox events.
     * In a production environment with multiple instances, you would need distributed locking (e.g., ShedLock or Redisson)
     * so that multiple servers don't pick up the exact same events simultaneously.
     */
    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void processOutboxEvents() {
        // Find all pending events, ordered by when they were created to maintain strict ordering
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        log.info("Found {} pending outbox events. Processing...", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            try {
                // Publish to Kafka. We use the aggregateId (Payment ID) as the partition key 
                // to ensure all events for the same payment go to the same Kafka partition and stay ordered!
                kafkaTemplate.send(TOPIC_NAME, event.getAggregateId(), event.getPayload()).get(); // .get() forces synchronous wait
                
                // If the send is successful, mark it as processed!
                event.setStatus("PROCESSED");
                event.setProcessedAt(LocalDateTime.now());
                
                // Spring Data JPA's dirty checking will automatically save this update 
                // because this method is @Transactional.
                
                log.info("Successfully published event ID {} to Kafka topic '{}'", event.getId(), TOPIC_NAME);
            } catch (Exception e) {
                // If Kafka is down, we catch the error and break the loop. 
                // The event stays in PENDING state and will be retried on the next poll.
                // This guarantees "At-Least-Once" delivery!
                log.error("Failed to publish outbox event ID {} to Kafka. Will retry later.", event.getId(), e);
                break; 
            }
        }
    }
}
