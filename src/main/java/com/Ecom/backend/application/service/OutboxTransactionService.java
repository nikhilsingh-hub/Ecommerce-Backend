package com.Ecom.backend.application.service;

import com.Ecom.backend.domain.entity.OutboxEvent;
import com.Ecom.backend.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Separate service to handle outbox event transactions properly.
 * This service is designed to be called asynchronously with proper transaction management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxTransactionService {
    
    private final OutboxEventRepository outboxEventRepository;
    
    /**
     * Mark an event as successfully processed.
     * This method runs asynchronously in its own transaction.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventAsProcessedAsync(OutboxEvent event) {
        try {
            int updated = outboxEventRepository.markAsProcessed(event.getId(), LocalDateTime.now());
            if (updated == 0) {
                log.warn("Failed to mark event {} as processed - may have been processed by another instance", 
                    event.getId());
            } else {
                log.debug("Successfully marked event {} as processed", event.getId());
            }
        } catch (Exception e) {
            log.error("Error marking event {} as processed", event.getId(), e);
        }
    }
    
    /**
     * Handle event processing failure.
     * This method runs asynchronously in its own transaction.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleEventFailureAsync(OutboxEvent event, Throwable error) {
        try {
            int newRetryCount = event.getRetryCount() + 1;
            LocalDateTime nextRetryAt = LocalDateTime.now()
                .plusMinutes((long) Math.pow(2, newRetryCount)); // Exponential backoff
            
            String errorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            
            int updated = outboxEventRepository.incrementRetryCount(event.getId(), nextRetryAt, errorMessage);
            
            if (updated == 0) {
                log.warn("Failed to update retry count for event {} - may have been processed by another instance", 
                    event.getId());
            } else {
                log.warn("Event {} failed processing (attempt {}), will retry at {}: {}", 
                    event.getId(), newRetryCount, nextRetryAt, errorMessage);
                
                // Log if event should be moved to dead letter (after 5 retries)
                if (newRetryCount >= 5) {
                    log.error("Event {} has exceeded maximum retries and should be moved to dead letter queue", 
                        event.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error handling failure for event {}", event.getId(), e);
        }
    }
}
