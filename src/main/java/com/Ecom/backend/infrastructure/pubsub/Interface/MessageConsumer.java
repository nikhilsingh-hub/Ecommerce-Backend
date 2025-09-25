package com.Ecom.backend.infrastructure.pubsub.Interface;

import com.Ecom.backend.infrastructure.pubsub.DTO.Message;
import com.Ecom.backend.infrastructure.pubsub.DTO.MessageBatch;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for consuming messages from the pub/sub system.
 * Supports batch consumption and parallel processing.
 * Designed to be easily replaceable with a real Kafka consumer.
 */
public interface MessageConsumer {
    
    /**
     * Subscribe to a topic with a consumer group
     * 
     * @param topic The topic to subscribe to
     * @param consumerGroup The consumer group identifier
     */
    void subscribe(String topic, String consumerGroup);
    
    /**
     * Subscribe to multiple topics
     * 
     * @param topics List of topics to subscribe to
     * @param consumerGroup The consumer group identifier
     */
    void subscribe(List<String> topics, String consumerGroup);
    
    /**
     * Set the message handler for processing individual messages
     * 
     * @param handler Function to process each message
     */
    void setMessageHandler(Consumer<Message> handler);
    
    /**
     * Set the batch handler for processing message batches
     * 
     * @param handler Function to process message batches
     */
    void setBatchHandler(Consumer<MessageBatch> handler);
    
    /**
     * Start consuming messages
     * This method returns immediately and processing happens asynchronously
     */
    void startConsuming();
    
    /**
     * Stop consuming messages
     */
    void stopConsuming();
    
    /**
     * Acknowledge successful processing of a message
     * 
     * @param message The message that was successfully processed
     */
    void acknowledge(Message message);
    
    /**
     * Acknowledge successful processing of a message batch
     * 
     * @param batch The batch that was successfully processed
     */
    void acknowledgeBatch(MessageBatch batch);
    
    /**
     * Mark a message as failed and potentially retry
     * 
     * @param message The message that failed processing
     * @param error The error that occurred
     */
    void markFailed(Message message, Throwable error);
    
    /**
     * Mark a batch as failed
     * 
     * @param batch The batch that failed processing
     * @param error The error that occurred
     */
    void markBatchFailed(MessageBatch batch, Throwable error);
    
    /**
     * Get consumer statistics
     * 
     * @return ConsumerStats object containing metrics
     */
    ConsumerStats getStats();
    
    /**
     * Close the consumer and release resources
     */
    void close();
    
    /**
     * Statistics for monitoring consumer performance
     */
    record ConsumerStats(
        long totalMessagesConsumed,
        long totalBatchesConsumed,
        long failedMessages,
        long retriedMessages,
        long deadLetterMessages,
        double averageProcessingTimeMs,
        long lastConsumeTimestamp,
        long currentOffset
    ) {}
}
