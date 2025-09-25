package com.Ecom.backend.infrastructure.pubsub;

import com.Ecom.backend.infrastructure.pubsub.DTO.Message;
import com.Ecom.backend.infrastructure.pubsub.DTO.MessageBatch;
import com.Ecom.backend.infrastructure.pubsub.Interface.MessageConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Factory for creating and managing multiple parallel message consumers.
 * Supports creating consumer groups with multiple worker instances.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageConsumerFactory {
    
    private final ApplicationContext applicationContext;
    
    @Value("${pubsub.consumer.default-worker-count:3}")
    private int defaultWorkerCount;
    
    private final ConcurrentHashMap<String, List<MessageConsumer>> consumerGroups = new ConcurrentHashMap<>();
    
    /**
     * Create a consumer group with multiple parallel workers
     * 
     * @param consumerGroupId Unique identifier for the consumer group
     * @param topics List of topics to subscribe to
     * @param workerCount Number of parallel consumer workers
     * @param messageHandler Handler for individual messages
     * @return List of created consumers
     */
    public List<MessageConsumer> createConsumerGroup(
            String consumerGroupId,
            List<String> topics,
            int workerCount,
            Consumer<Message> messageHandler) {
        
        log.info("Creating consumer group {} with {} workers for topics {}", 
            consumerGroupId, workerCount, topics);
        
        List<MessageConsumer> consumers = new ArrayList<>();
        
        for (int i = 0; i < workerCount; i++) {
            String workerId = consumerGroupId + "-worker-" + i;
            
            InMemoryMessageConsumer consumer = applicationContext.getBean(InMemoryMessageConsumer.class);
            consumer.subscribe(topics, workerId);
            consumer.setMessageHandler(messageHandler);
            
            consumers.add(consumer);
        }
        
        consumerGroups.put(consumerGroupId, consumers);
        log.info("Created consumer group {} with {} workers for topics {}", 
            consumerGroupId, workerCount, topics);
        
        return consumers;
    }
    
    /**
     * Create a consumer group with batch processing
     * 
     * @param consumerGroupId Unique identifier for the consumer group
     * @param topics List of topics to subscribe to
     * @param workerCount Number of parallel consumer workers
     * @param batchHandler Handler for message batches
     * @return List of created consumers
     */
    public List<MessageConsumer> createBatchConsumerGroup(
            String consumerGroupId,
            List<String> topics,
            int workerCount,
            Consumer<MessageBatch> batchHandler) {
        
        List<MessageConsumer> consumers = new ArrayList<>();
        
        for (int i = 0; i < workerCount; i++) {
            InMemoryMessageConsumer consumer = applicationContext.getBean(InMemoryMessageConsumer.class);
            String workerId = consumerGroupId + "-batch-worker-" + i;
            
            consumer.subscribe(topics, workerId);
            consumer.setBatchHandler(batchHandler);
            
            consumers.add(consumer);
        }
        
        consumerGroups.put(consumerGroupId, consumers);
        log.info("Created batch consumer group {} with {} workers for topics {}", 
            consumerGroupId, workerCount, topics);
        
        return consumers;
    }
    
    /**
     * Create a consumer group with default worker count
     */
    public List<MessageConsumer> createConsumerGroup(
            String consumerGroupId,
            List<String> topics,
            Consumer<Message> messageHandler) {
        return createConsumerGroup(consumerGroupId, topics, defaultWorkerCount, messageHandler);
    }
    
    /**
     * Start all consumers in a consumer group
     */
    public void startConsumerGroup(String consumerGroupId) {
        List<MessageConsumer> consumers = consumerGroups.get(consumerGroupId);
        if (consumers != null) {
            consumers.forEach(MessageConsumer::startConsuming);
            log.info("Started consumer group {} with {} workers", 
                consumerGroupId, consumers.size());
        } else {
            log.error("Consumer group not found: {}", consumerGroupId);
            throw new IllegalArgumentException("Consumer group not found: " + consumerGroupId);
        }
    }
    
    /**
     * Stop all consumers in a consumer group
     */
    public void stopConsumerGroup(String consumerGroupId) {
        List<MessageConsumer> consumers = consumerGroups.get(consumerGroupId);
        if (consumers != null) {
            consumers.forEach(MessageConsumer::stopConsuming);
            log.info("Stopped consumer group {} with {} workers", consumerGroupId, consumers.size());
        }
    }
    
    /**
     * Get statistics for all consumers in a group
     */
    public ConsumerGroupStats getConsumerGroupStats(String consumerGroupId) {
        List<MessageConsumer> consumers = consumerGroups.get(consumerGroupId);
        if (consumers == null) {
            throw new IllegalArgumentException("Consumer group not found: " + consumerGroupId);
        }
        
        long totalMessages = 0;
        long totalBatches = 0;
        long totalFailed = 0;
        long totalRetried = 0;
        long totalDeadLetter = 0;
        double totalProcessingTime = 0;
        
        for (MessageConsumer consumer : consumers) {
            MessageConsumer.ConsumerStats stats = consumer.getStats();
            totalMessages += stats.totalMessagesConsumed();
            totalBatches += stats.totalBatchesConsumed();
            totalFailed += stats.failedMessages();
            totalRetried += stats.retriedMessages();
            totalDeadLetter += stats.deadLetterMessages();
            totalProcessingTime += stats.averageProcessingTimeMs();
        }
        
        double averageProcessingTime = consumers.size() > 0 ? 
            totalProcessingTime / consumers.size() : 0;
        
        return new ConsumerGroupStats(
            consumerGroupId,
            consumers.size(),
            totalMessages,
            totalBatches,
            totalFailed,
            totalRetried,
            totalDeadLetter,
            averageProcessingTime
        );
    }
    
    /**
     * Shutdown all consumer groups
     */
    public void shutdown() {
        consumerGroups.forEach((groupId, consumers) -> {
            consumers.forEach(MessageConsumer::close);
        });
        consumerGroups.clear();
        log.info("Shutdown all consumer groups");
    }
    
    /**
     * Get all consumer group IDs
     */
    public List<String> getConsumerGroupIds() {
        return new ArrayList<>(consumerGroups.keySet());
    }
    
    /**
     * Statistics for a consumer group
     */
    public record ConsumerGroupStats(
        String consumerGroupId,
        int workerCount,
        long totalMessagesConsumed,
        long totalBatchesConsumed,
        long totalFailedMessages,
        long totalRetriedMessages,
        long totalDeadLetterMessages,
        double averageProcessingTimeMs
    ) {}
}
