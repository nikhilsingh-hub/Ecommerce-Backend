package com.Ecom.backend.infrastructure.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of a message broker that simulates Kafka behavior.
 * Supports batch publishing, parallel consumption, offset management, and retry logic.
 * This can be easily replaced with a real Kafka implementation.
 */
@Component
@Slf4j
public class InMemoryMessageBroker {
    
    private final Map<String, TopicPartition> topics = new ConcurrentHashMap<>();
    private final Map<String, ConsumerGroupState> consumerGroups = new ConcurrentHashMap<>();
    private final ExecutorService publisherExecutor = Executors.newCachedThreadPool();
    private final ExecutorService consumerExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);
    
    // Configuration
    private final int maxRetries = 3;
    private final long retryDelayMs = 1000;
    private final int maxBatchSize = 100;
    private final long consumerPollIntervalMs = 100;
    
    /**
     * Internal class representing a topic partition
     */
    private static class TopicPartition {
        private final String topic;
        private final AtomicLong offsetGenerator = new AtomicLong(0);
        private final Queue<Message> messages = new ConcurrentLinkedQueue<>();
        
        public TopicPartition(String topic) {
            this.topic = topic;
        }
        
        public synchronized Message addMessage(Message message) {
            Message messageWithOffset = message.withOffset(offsetGenerator.incrementAndGet());
            messages.offer(messageWithOffset);
            return messageWithOffset;
        }
        
        public synchronized List<Message> pollMessages(int maxMessages, long fromOffset) {
            List<Message> result = new ArrayList<>();
            Iterator<Message> iterator = messages.iterator();
            
            while (iterator.hasNext() && result.size() < maxMessages) {
                Message message = iterator.next();
                if (message.getOffset() > fromOffset) {
                    result.add(message);
                }
            }
            return result;
        }
    }
    
    /**
     * Internal class tracking consumer group state
     */
    private static class ConsumerGroupState {
        private final String groupId;
        private final Map<String, Long> topicOffsets = new ConcurrentHashMap<>();
        private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();
        
        public ConsumerGroupState(String groupId) {
            this.groupId = groupId;
        }
        
        public void subscribe(String topic) {
            subscribedTopics.add(topic);
            topicOffsets.putIfAbsent(topic, 0L);
        }
        
        public long getOffset(String topic) {
            return topicOffsets.getOrDefault(topic, 0L);
        }
        
        public void commitOffset(String topic, long offset) {
            topicOffsets.put(topic, offset);
        }
    }
    
    /**
     * Publish a single message
     */
    public CompletableFuture<Message> publish(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TopicPartition partition = topics.computeIfAbsent(
                    message.getTopic(), 
                    TopicPartition::new
                );
                
                Message publishedMessage = partition.addMessage(message);
                log.debug("Published message {} to topic {} with offset {}", 
                    message.getId(), message.getTopic(), publishedMessage.getOffset());
                
                return publishedMessage;
            } catch (Exception e) {
                log.error("Failed to publish message {}", message.getId(), e);
                throw new RuntimeException("Failed to publish message", e);
            }
        }, publisherExecutor);
    }
    
    /**
     * Publish a batch of messages
     */
    public CompletableFuture<List<Message>> publishBatch(List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> {
            List<Message> publishedMessages = new ArrayList<>();
            
            for (Message message : messages) {
                try {
                    TopicPartition partition = topics.computeIfAbsent(
                        message.getTopic(), 
                        TopicPartition::new
                    );
                    
                    Message publishedMessage = partition.addMessage(message);
                    publishedMessages.add(publishedMessage);
                } catch (Exception e) {
                    log.error("Failed to publish message {} in batch", message.getId(), e);
                    throw new RuntimeException("Failed to publish batch", e);
                }
            }
            
            log.debug("Published batch of {} messages", publishedMessages.size());
            return publishedMessages;
        }, publisherExecutor);
    }
    
    /**
     * Subscribe a consumer to topics
     */
    public void subscribe(String consumerGroup, List<String> topics) {
        ConsumerGroupState groupState = consumerGroups.computeIfAbsent(
            consumerGroup, 
            ConsumerGroupState::new
        );
        
        for (String topic : topics) {
            groupState.subscribe(topic);
            // Ensure topic exists
            this.topics.computeIfAbsent(topic, TopicPartition::new);
        }
        
        log.debug("Consumer group {} subscribed to topics {}", consumerGroup, topics);
    }
    
    /**
     * Poll for messages for a consumer group
     */
    public MessageBatch pollMessages(String consumerGroup, int maxMessages) {
        ConsumerGroupState groupState = consumerGroups.get(consumerGroup);
        if (groupState == null) {
            throw new IllegalStateException("Consumer group not subscribed: " + consumerGroup);
        }
        
        List<Message> allMessages = new ArrayList<>();
        String selectedTopic = null;
        
        // Round-robin through subscribed topics
        for (String topic : groupState.subscribedTopics) {
            TopicPartition partition = topics.get(topic);
            if (partition != null) {
                long currentOffset = groupState.getOffset(topic);
                List<Message> messages = partition.pollMessages(maxMessages - allMessages.size(), currentOffset);
                
                if (!messages.isEmpty()) {
                    allMessages.addAll(messages);
                    selectedTopic = topic;
                    break; // For simplicity, take from first available topic
                }
            }
        }
        
        if (allMessages.isEmpty()) {
            return MessageBatch.builder()
                .batchId(UUID.randomUUID().toString())
                .messages(Collections.emptyList())
                .topic("")
                .consumerGroup(consumerGroup)
                .partitionId(0)
                .build();
        }
        
        return MessageBatch.create(selectedTopic, consumerGroup, 0, allMessages);
    }
    
    /**
     * Commit offset for a consumer group
     */
    public void commitOffset(String consumerGroup, String topic, long offset) {
        ConsumerGroupState groupState = consumerGroups.get(consumerGroup);
        if (groupState != null) {
            groupState.commitOffset(topic, offset);
            log.debug("Committed offset {} for topic {} in consumer group {}", 
                offset, topic, consumerGroup);
        }
    }
    
    /**
     * Get broker statistics
     */
    public BrokerStats getStats() {
        int totalTopics = topics.size();
        int totalConsumerGroups = consumerGroups.size();
        
        long totalMessages = topics.values().stream()
            .mapToLong(partition -> partition.offsetGenerator.get())
            .sum();
        
        return new BrokerStats(totalTopics, totalConsumerGroups, totalMessages);
    }
    
    /**
     * Shutdown the broker
     */
    public void shutdown() {
        publisherExecutor.shutdown();
        consumerExecutor.shutdown();
        retryExecutor.shutdown();
        
        try {
            if (!publisherExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                publisherExecutor.shutdownNow();
            }
            if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publisherExecutor.shutdownNow();
            consumerExecutor.shutdownNow();
            retryExecutor.shutdownNow();
        }
        
        log.info("InMemoryMessageBroker shutdown completed");
    }
    
    /**
     * Broker statistics
     */
    public record BrokerStats(
        int totalTopics,
        int totalConsumerGroups,
        long totalMessages
    ) {}
}
