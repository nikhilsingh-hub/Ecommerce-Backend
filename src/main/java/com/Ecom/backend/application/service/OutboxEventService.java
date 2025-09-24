package com.Ecom.backend.application.service;

import com.Ecom.backend.domain.entity.OutboxEvent;
import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessagePublisher;
import com.Ecom.backend.infrastructure.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service implementing the Transactional Outbox Pattern.
 * 
 * This service ensures reliable event publishing by:
 * 1. Storing events in the same database transaction as business changes
 * 2. Processing events asynchronously to publish them to the message broker
 * 3. Handling retries and dead letter processing with exponential backoff
 * 4. Maintaining exactly-once semantics i.e each message will be
 * 
 * Two independent scheduled processors handle different event types:
 * - Fresh events (retryCount = 0): Processed every ~5 seconds for fast delivery
 * - Retry events (retryCount > 0): Processed every ~10 seconds with backoff logic
 * 
 * This separation prevents race conditions and ensures optimal processing patterns.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    private final OutboxTransactionService outboxTransactionService;
    
    @Value("${outbox.batch-size:50}")
    private int batchSize;
    
    @Value("${outbox.processing-interval-ms:5000}")
    private long processingIntervalMs;
    
    @Value("${outbox.cleanup-after-days:7}")
    private int cleanupAfterDays;
    
    /**
     * Store an event in the outbox within the current transaction.
     * This method MUST be called within an existing transaction.
     * 
     * @param aggregateId The ID of the aggregate that generated the event
     * @param aggregateType The type of aggregate (e.g., "Product")
     * @param eventType The type of event (e.g., "ProductCreated")
     * @param eventData The event payload as an object
     * @return The saved OutboxEvent
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent storeEvent(String aggregateId, String aggregateType, String eventType, Object eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);
            
            OutboxEvent event = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .eventData(eventDataJson)
                .processed(false)
                .retryCount(0)
                .build();
            
            OutboxEvent savedEvent = outboxEventRepository.save(event);
            
            log.debug("Stored outbox event {} for aggregate {} of type {}", 
                savedEvent.getId(), aggregateId, eventType);
            
            return savedEvent;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event data for aggregate {}: {}", aggregateId, e.getMessage());
            throw new RuntimeException("Failed to serialize event data", e);
        }
    }
    
    /**
     * Process fresh (never attempted) events from the outbox.
     * This method runs on a scheduled basis to handle new events quickly.
     * Only processes events with retryCount = 0 to avoid overlap with retry processing.
     */
    @Scheduled(fixedDelayString = "${outbox.processing-interval-ms:5000}")
    @Async
    public CompletableFuture<Void> processOutboxEvents() {
        try {
            List<OutboxEvent> freshEvents = outboxEventRepository
                .findFreshUnprocessedEvents(PageRequest.of(0, batchSize));
            
            if (freshEvents.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            
            log.debug("Processing {} fresh outbox events", freshEvents.size());
            
            for (OutboxEvent event : freshEvents) {
                processEvent(event);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error processing fresh outbox events", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Process events that failed previously and are eligible for retry.
     * This method runs less frequently to handle failed events with exponential backoff.
     * Only processes events with retryCount > 0 and nextRetryAt <= now to avoid overlap with fresh processing.
     */
    @Scheduled(fixedDelayString = "${outbox.retry-interval-ms:10000}")
    @Async
    public CompletableFuture<Void> processRetryableEvents() {
        try {
            List<OutboxEvent> retryableEvents = outboxEventRepository
                .findEventsForRetry(LocalDateTime.now(), PageRequest.of(0, batchSize));
            
            if (retryableEvents.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            
            log.debug("Processing {} retryable outbox events", retryableEvents.size());
            
            for (OutboxEvent event : retryableEvents) {
                processEvent(event);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error processing retryable outbox events", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Process a single outbox event
     */
    @Async
    public CompletableFuture<Void> processEvent(OutboxEvent event) {
        try {
            // Create message for pub/sub system
            Map<String, String> headers = createMessageHeaders(event);
            
            Message message = Message.create(
                getTopicForEvent(event),
                event.getEventType(),
                event.getEventData(),
                headers,
                event.getAggregateId()
            );
            
            // Publish message asynchronously
            return messagePublisher.publish(message)
                .thenAccept(publishedMessage -> {
                    outboxTransactionService.markEventAsProcessedAsync(event);
                    log.debug("Successfully published event {} with message ID {}", 
                        event.getId(), publishedMessage.getId());
                })
                .exceptionally(throwable -> {
                    // Handle failure using separate transaction service  
                    outboxTransactionService.handleEventFailureAsync(event, throwable);
                    return null;
                });
                
        } catch (Exception e) {
            outboxTransactionService.handleEventFailureAsync(event, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Create message headers including idempotency key
     */
    private Map<String, String> createMessageHeaders(OutboxEvent event) {
        Map<String, String> headers = new HashMap<>();
        headers.put("idempotency-key", "outbox-event-" + event.getId());
        headers.put("aggregate-id", event.getAggregateId());
        headers.put("aggregate-type", event.getAggregateType());
        headers.put("event-type", event.getEventType());
        headers.put("source", "outbox-service");
        headers.put("created-at", event.getCreatedAt().toString());
        return headers;
    }
    
    /**
     * Determine the topic for an event based on aggregate type
     */
    private String getTopicForEvent(OutboxEvent event) {
        return switch (event.getAggregateType().toLowerCase()) {
            case "product" -> "product-events";
            case "order" -> "order-events";
            case "user" -> "user-events";
            default -> "general-events";
        };
    }
    
    
    /**
     * Handle events that have exceeded retry limits
     */
    private void handleDeadLetterEvent(OutboxEvent event, Throwable error) {
        log.error("Event {} moved to dead letter after {} retries. Final error: {}", 
            event.getId(), event.getRetryCount(), error.getMessage());
        
        // In a production system, you might:
        // 1. Send to a dead letter topic
        // 2. Send alerts to operations team
        // 3. Store in a separate dead letter table
        // For now, we just log it
    }
    
    /**
     * Clean up old processed events
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanupAfterDays);
        
        try {
            int deletedCount = outboxEventRepository.deleteOldProcessedEvents(cutoffDate);
            if (deletedCount > 0) {
                log.info("Cleaned up {} old processed outbox events older than {}", 
                    deletedCount, cutoffDate);
            }
        } catch (Exception e) {
            log.error("Error cleaning up old outbox events", e);
        }
    }
    
    /**
     * Get outbox service statistics
     */
    public OutboxStats getStats() {
        try {
            long unprocessedCount = outboxEventRepository.countUnprocessedEvents();
            List<Object[]> statusStats = outboxEventRepository.getEventStatusStatistics();
            
            return new OutboxStats(unprocessedCount, statusStats.size());
            
        } catch (Exception e) {
            log.error("Error getting outbox statistics", e);
            return new OutboxStats(0, 0);
        }
    }
    
    /**
     * Outbox service statistics
     */
    public record OutboxStats(
        long unprocessedEventCount,
        int totalEventTypes
    ) {}
}
