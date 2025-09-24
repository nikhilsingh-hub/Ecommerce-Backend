package com.Ecom.backend.infrastructure.pubsub;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a message in the pub/sub system.
 * Immutable value object following DDD principles.
 */
@Value
@Builder
public class Message {
    
    private final String id;
    private final String topic;
    private final String eventType;
    private final String payload;
    private final Map<String, String> headers;
    private final LocalDateTime timestamp;
    private final String partitionKey;
    private final Long offset;
    
    /**
     * Create a message with generated ID and timestamp
     */
    public static Message create(String topic, String eventType, String payload, Map<String, String> headers, String partitionKey) {
        return Message.builder()
            .id(java.util.UUID.randomUUID().toString())
            .topic(topic)
            .eventType(eventType)
            .payload(payload)
            .headers(headers)
            .timestamp(LocalDateTime.now())
            .partitionKey(partitionKey)
            .build();
    }
    
    /**
     * Create a copy with a specific offset (used internally by the pub/sub system)
     */
    public Message withOffset(Long offset) {
        return Message.builder()
            .id(this.id)
            .topic(this.topic)
            .eventType(this.eventType)
            .payload(this.payload)
            .headers(this.headers)
            .timestamp(this.timestamp)
            .partitionKey(this.partitionKey)
            .offset(offset)
            .build();
    }
}
