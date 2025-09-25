package com.Ecom.backend.infrastructure.pubsub.Interface;

import com.Ecom.backend.infrastructure.pubsub.DTO.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for publishing messages to the pub/sub system.
 * Designed to be easily replaceable with a real Kafka producer.
 * Follows the Interface Segregation Principle.
 */
public interface MessagePublisher {
    
    /**
     * Publish a single message asynchronously
     * 
     * @param message The message to publish
     * @return Future containing the published message with offset
     */
    CompletableFuture<Message> publish(Message message);
    
    /**
     * Publish a batch of messages asynchronously
     * 
     * @param messages List of messages to publish
     * @return Future containing the list of published messages with offsets
     */
    CompletableFuture<List<Message>> publishBatch(List<Message> messages);
    
    /**
     * Publish a message to a specific topic
     * 
     * @param topic The topic to publish to
     * @param eventType The type of event
     * @param payload The message payload
     * @param partitionKey Key for partitioning (optional)
     * @return Future containing the published message with offset
     */
    CompletableFuture<Message> publish(String topic, String eventType, String payload, String partitionKey);
    
    /**
     * Get publisher statistics
     * 
     * @return PublisherStats object containing metrics
     */
    PublisherStats getStats();
    
    /**
     * Close the publisher and release resources
     */
    void close();
    
    /**
     * Statistics for monitoring publisher performance
     */
    record PublisherStats(
        long totalMessagesPublished,
        long totalBatchesPublished,
        long failedPublishes,
        double averageBatchSize,
        long lastPublishTimestamp
    ) {}
}
