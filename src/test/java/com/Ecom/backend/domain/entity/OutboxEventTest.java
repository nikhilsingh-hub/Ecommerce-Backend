package com.Ecom.backend.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OutboxEvent domain entity.
 * Tests outbox pattern business logic and retry mechanisms.
 */
@DisplayName("OutboxEvent Entity Tests")
class OutboxEventTest {
    
    private OutboxEvent outboxEvent;
    
    @BeforeEach
    void setUp() {
        outboxEvent = OutboxEvent.builder()
            .id(1L)
            .aggregateId("123")
            .aggregateType("Product")
            .eventType("ProductCreated")
            .eventData("{\"productId\": 123}")
            .processed(false)
            .retryCount(0)
            .build();
    }
    
    @Test
    @DisplayName("Should mark event as processed")
    void shouldMarkAsProcessed() {
        // Given
        assertThat(outboxEvent.getProcessed()).isFalse();
        assertThat(outboxEvent.getProcessedAt()).isNull();
        
        // When
        outboxEvent.markAsProcessed();
        
        // Then
        assertThat(outboxEvent.getProcessed()).isTrue();
        assertThat(outboxEvent.getProcessedAt()).isNotNull();
        assertThat(outboxEvent.getErrorMessage()).isNull();
    }
    
    @Test
    @DisplayName("Should mark event as failed and schedule retry")
    void shouldMarkAsFailed() {
        // Given
        String errorMessage = "Processing failed";
        int initialRetryCount = outboxEvent.getRetryCount();
        
        // When
        outboxEvent.markAsFailed(errorMessage);
        
        // Then
        assertThat(outboxEvent.getRetryCount()).isEqualTo(initialRetryCount + 1);
        assertThat(outboxEvent.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(outboxEvent.getNextRetryAt()).isNotNull();
        assertThat(outboxEvent.getNextRetryAt()).isAfter(LocalDateTime.now());
    }
    
    @Test
    @DisplayName("Should use exponential backoff for retry scheduling")
    void shouldUseExponentialBackoffForRetry() {
        // Given
        outboxEvent.setRetryCount(2);
        
        // When
        outboxEvent.markAsFailed("Test error");
        
        // Then
        // Retry count should be 3, so next retry should be 2^3 = 8 minutes from now
        LocalDateTime expectedRetryTime = LocalDateTime.now().plusMinutes(8);
        assertThat(outboxEvent.getNextRetryAt()).isBetween(
            expectedRetryTime.minusSeconds(5), 
            expectedRetryTime.plusSeconds(5)
        );
    }
    
    @Test
    @DisplayName("Should be eligible for retry when conditions are met")
    void shouldBeEligibleForRetry() {
        // Given
        outboxEvent.setProcessed(false);
        outboxEvent.setRetryCount(2);
        outboxEvent.setNextRetryAt(LocalDateTime.now().minusMinutes(1)); // Past time
        
        // When & Then
        assertThat(outboxEvent.isEligibleForRetry()).isTrue();
    }
    
    @Test
    @DisplayName("Should not be eligible for retry when already processed")
    void shouldNotBeEligibleForRetryWhenProcessed() {
        // Given
        outboxEvent.setProcessed(true);
        outboxEvent.setRetryCount(2);
        
        // When & Then
        assertThat(outboxEvent.isEligibleForRetry()).isFalse();
    }
    
    @Test
    @DisplayName("Should not be eligible for retry when max retries exceeded")
    void shouldNotBeEligibleForRetryWhenMaxRetriesExceeded() {
        // Given
        outboxEvent.setProcessed(false);
        outboxEvent.setRetryCount(5); // Max retries
        
        // When & Then
        assertThat(outboxEvent.isEligibleForRetry()).isFalse();
    }
    
    @Test
    @DisplayName("Should not be eligible for retry when next retry time is in future")
    void shouldNotBeEligibleForRetryWhenNextRetryTimeInFuture() {
        // Given
        outboxEvent.setProcessed(false);
        outboxEvent.setRetryCount(2);
        outboxEvent.setNextRetryAt(LocalDateTime.now().plusMinutes(5)); // Future time
        
        // When & Then
        assertThat(outboxEvent.isEligibleForRetry()).isFalse();
    }
    
    @Test
    @DisplayName("Should be eligible for retry when next retry time is null")
    void shouldBeEligibleForRetryWhenNextRetryTimeIsNull() {
        // Given
        outboxEvent.setProcessed(false);
        outboxEvent.setRetryCount(2);
        outboxEvent.setNextRetryAt(null);
        
        // When & Then
        assertThat(outboxEvent.isEligibleForRetry()).isTrue();
    }
    
    @Test
    @DisplayName("Should move to dead letter when max retries exceeded")
    void shouldMoveToDeadLetterWhenMaxRetriesExceeded() {
        // Given
        outboxEvent.setProcessed(false);
        outboxEvent.setRetryCount(5);
        
        // When & Then
        assertThat(outboxEvent.shouldMoveToDeadLetter()).isTrue();
    }
    
    @Test
    @DisplayName("Should not move to dead letter when processed")
    void shouldNotMoveToDeadLetterWhenProcessed() {
        // Given
        outboxEvent.setProcessed(true);
        outboxEvent.setRetryCount(5);
        
        // When & Then
        assertThat(outboxEvent.shouldMoveToDeadLetter()).isFalse();
    }
    
    @Test
    @DisplayName("Should not move to dead letter when retries not exhausted")
    void shouldNotMoveToDeadLetterWhenRetriesNotExhausted() {
        // Given
        outboxEvent.setProcessed(false);
        outboxEvent.setRetryCount(3);
        
        // When & Then
        assertThat(outboxEvent.shouldMoveToDeadLetter()).isFalse();
    }
}
