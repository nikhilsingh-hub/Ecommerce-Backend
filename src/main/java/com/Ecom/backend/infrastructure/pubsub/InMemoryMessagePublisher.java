package com.Ecom.backend.infrastructure.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of MessagePublisher.
 * Delegates to InMemoryMessageBroker for actual message handling.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InMemoryMessagePublisher implements MessagePublisher {
    
    private final InMemoryMessageBroker broker;
    
    // Statistics tracking
    private final AtomicLong totalMessagesPublished = new AtomicLong(0);
    private final AtomicLong totalBatchesPublished = new AtomicLong(0);
    private final AtomicLong failedPublishes = new AtomicLong(0);
    private final AtomicLong totalBatchSize = new AtomicLong(0);
    private volatile long lastPublishTimestamp = 0;
    
    @Override
    public CompletableFuture<Message> publish(Message message) {
        return broker.publish(message)
            .whenComplete((result, throwable) -> {
                if (throwable == null) {
                    totalMessagesPublished.incrementAndGet();
                    lastPublishTimestamp = System.currentTimeMillis();
                    log.debug("Successfully published message {} to topic {}", 
                        result.getId(), result.getTopic());
                } else {
                    failedPublishes.incrementAndGet();
                    log.error("Failed to publish message {} to topic {}", 
                        message.getId(), message.getTopic(), throwable);
                }
            });
    }
    
    @Override
    public CompletableFuture<List<Message>> publishBatch(List<Message> messages) {
        if (messages.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        
        return broker.publishBatch(messages)
            .whenComplete((result, throwable) -> {
                if (throwable == null) {
                    totalMessagesPublished.addAndGet(result.size());
                    totalBatchesPublished.incrementAndGet();
                    totalBatchSize.addAndGet(result.size());
                    lastPublishTimestamp = System.currentTimeMillis();
                    log.debug("Successfully published batch of {} messages", result.size());
                } else {
                    failedPublishes.incrementAndGet();
                    log.error("Failed to publish batch of {} messages", messages.size(), throwable);
                }
            });
    }
    
    @Override
    public CompletableFuture<Message> publish(String topic, String eventType, String payload, String partitionKey) {
        Message message = Message.create(topic, eventType, payload, new HashMap<>(), partitionKey);
        return publish(message);
    }
    
    @Override
    public PublisherStats getStats() {
        long totalMessages = totalMessagesPublished.get();
        long totalBatches = totalBatchesPublished.get();
        double averageBatchSize = totalBatches > 0 ? 
            (double) totalBatchSize.get() / totalBatches : 0.0;
        
        return new PublisherStats(
            totalMessages,
            totalBatches,
            failedPublishes.get(),
            averageBatchSize,
            lastPublishTimestamp
        );
    }
    
    @Override
    public void close() {
        log.info("InMemoryMessagePublisher closed. Final stats: {}", getStats());
    }
}
