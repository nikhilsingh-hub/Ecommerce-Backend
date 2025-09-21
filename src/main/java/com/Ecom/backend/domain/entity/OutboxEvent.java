package com.Ecom.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * OutboxEvent entity implementing the Transactional Outbox Pattern.
 * This ensures reliable event publishing by storing events in the same
 * database transaction as business data changes.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_processed", columnList = "processed"),
    @Index(name = "idx_outbox_event_type", columnList = "eventType"),
    @Index(name = "idx_outbox_created_at", columnList = "createdAt"),
    @Index(name = "idx_outbox_aggregate_id", columnList = "aggregateId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String eventData;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column
    private LocalDateTime processedAt;

    @Column
    @Builder.Default
    private Integer retryCount = 0;

    @Column
    private LocalDateTime nextRetryAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    private Long version;

    /**
     * Mark the event as processed
     */
    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * Mark the event as failed and schedule retry
     */
    public void markAsFailed(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        // Exponential backoff: 2^retryCount minutes
        this.nextRetryAt = LocalDateTime.now().plusMinutes((long) Math.pow(2, retryCount));
    }

    /**
     * Check if event is eligible for retry
     */
    public boolean isEligibleForRetry() {
        return !processed && 
               retryCount < 5 && // Max 5 retries
               (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }

    /**
     * Check if event should be moved to dead letter queue
     */
    public boolean shouldMoveToDeadLetter() {
        return !processed && retryCount >= 5;
    }
}
