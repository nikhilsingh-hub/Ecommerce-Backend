package com.Ecom.backend.infrastructure.pubsub;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a batch of messages for efficient processing.
 * Includes metadata for tracking and acknowledgment.
 */
@Data
@Builder
public class MessageBatch {
    
    private final String batchId;
    private final List<Message> messages;
    private final String topic;
    private final String consumerGroup;
    private final Long startOffset;
    private final Long endOffset;
    private final LocalDateTime createdAt;
    private final int partitionId;
    
    /**
     * Create a batch from a list of messages
     */
    public static MessageBatch create(String topic, String consumerGroup, int partitionId, List<Message> messages) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Message batch cannot be empty");
        }
        
        Long startOffset = messages.get(0).getOffset();
        Long endOffset = messages.get(messages.size() - 1).getOffset();
        
        return MessageBatch.builder()
            .batchId(java.util.UUID.randomUUID().toString())
            .messages(messages)
            .topic(topic)
            .consumerGroup(consumerGroup)
            .partitionId(partitionId)
            .startOffset(startOffset)
            .endOffset(endOffset)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Get the size of the batch
     */
    public int size() {
        return messages.size();
    }
    
    /**
     * Check if batch is empty
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    /**
     * Get a specific message by index
     */
    public Message getMessage(int index) {
        return messages.get(index);
    }
}
