package com.payment.payflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The type of event, e.g., "PaymentCreated", "PaymentFailed"
    @Column(nullable = false)
    private String eventType;

    // The ID of the primary entity this event relates to (e.g., the Payment ID)
    @Column(nullable = false)
    private String aggregateId;

    // The actual JSON data we want to send to Kafka
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    // Status of the event: PENDING or PROCESSED
    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime processedAt;
}
