package com.Ecom.backend.infrastructure.pubsub;

import com.Ecom.backend.infrastructure.pubsub.Broker.InMemoryMessageBroker;
import com.Ecom.backend.infrastructure.pubsub.DTO.Message;
import com.Ecom.backend.infrastructure.pubsub.DTO.MessageBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Unit tests for InMemoryMessageBroker.
 * Tests pub/sub functionality, batch processing, and offset management.
 */
@DisplayName("InMemoryMessageBroker Tests")
class InMemoryMessageBrokerTest {
    
    private InMemoryMessageBroker broker;
    
    @BeforeEach
    void setUp() {
        broker = new InMemoryMessageBroker();
    }
    
    @Test
    @DisplayName("Should publish single message and assign offset")
    void shouldPublishSingleMessage() throws Exception {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("source", "test");
        
        Message message = Message.create("test-topic", "TestEvent", "test payload", headers, "test-key");
        
        // When
        CompletableFuture<Message> future = broker.publish(message);
        Message publishedMessage = future.get();
        
        // Then
        assertThat(publishedMessage.getId()).isEqualTo(message.getId());
        assertThat(publishedMessage.getOffset()).isEqualTo(1L);
        assertThat(publishedMessage.getTopic()).isEqualTo("test-topic");
        assertThat(publishedMessage.getEventType()).isEqualTo("TestEvent");
        assertThat(publishedMessage.getPayload()).isEqualTo("test payload");
    }
    
    @Test
    @DisplayName("Should publish batch of messages with sequential offsets")
    void shouldPublishBatchOfMessages() throws Exception {
        // Given
        List<Message> messages = List.of(
            Message.create("test-topic", "Event1", "payload1", new HashMap<>(), "key1"),
            Message.create("test-topic", "Event2", "payload2", new HashMap<>(), "key2"),
            Message.create("test-topic", "Event3", "payload3", new HashMap<>(), "key3")
        );
        
        // When
        CompletableFuture<List<Message>> future = broker.publishBatch(messages);
        List<Message> publishedMessages = future.get();
        
        // Then
        assertThat(publishedMessages).hasSize(3);
        assertThat(publishedMessages.get(0).getOffset()).isEqualTo(1L);
        assertThat(publishedMessages.get(1).getOffset()).isEqualTo(2L);
        assertThat(publishedMessages.get(2).getOffset()).isEqualTo(3L);
    }
    
    @Test
    @DisplayName("Should subscribe consumer group to topics")
    void shouldSubscribeConsumerGroup() {
        // Given
        String consumerGroup = "test-group";
        List<String> topics = List.of("topic1", "topic2");
        
        // When
        broker.subscribe(consumerGroup, topics);
        
        // Then
        // Verify by attempting to poll (should not throw exception)
        MessageBatch batch = broker.pollMessages(consumerGroup, 10);
        assertThat(batch).isNotNull();
        assertThat(batch.getMessages()).isEmpty();
    }
    
    @Test
    @DisplayName("Should poll messages for subscribed consumer group")
    void shouldPollMessagesForSubscribedGroup() throws Exception {
        // Given
        String consumerGroup = "test-group";
        String topic = "test-topic";
        
        broker.subscribe(consumerGroup, List.of(topic));
        
        // Publish some messages
        Message message1 = Message.create(topic, "Event1", "payload1", new HashMap<>(), "key1");
        Message message2 = Message.create(topic, "Event2", "payload2", new HashMap<>(), "key2");
        
        broker.publish(message1).get();
        broker.publish(message2).get();
        
        // When
        MessageBatch batch = broker.pollMessages(consumerGroup, 10);
        
        // Then
        assertThat(batch.getMessages()).hasSize(2);
        assertThat(batch.getTopic()).isEqualTo(topic);
        assertThat(batch.getConsumerGroup()).isEqualTo(consumerGroup);
    }
    
    @Test
    @DisplayName("Should respect max messages limit when polling")
    void shouldRespectMaxMessagesLimit() throws Exception {
        // Given
        String consumerGroup = "test-group";
        String topic = "test-topic";
        
        broker.subscribe(consumerGroup, List.of(topic));
        
        // Publish 5 messages
        for (int i = 0; i < 5; i++) {
            Message message = Message.create(topic, "Event" + i, "payload" + i, new HashMap<>(), "key" + i);
            broker.publish(message).get();
        }
        
        // When - poll with max 3 messages
        MessageBatch batch = broker.pollMessages(consumerGroup, 3);
        
        // Then
        assertThat(batch.getMessages()).hasSize(3);
    }
    
    @Test
    @DisplayName("Should commit offset for consumer group")
    void shouldCommitOffsetForConsumerGroup() {
        // Given
        String consumerGroup = "test-group";
        String topic = "test-topic";
        long offset = 5L;
        
        broker.subscribe(consumerGroup, List.of(topic));
        
        // When
        broker.commitOffset(consumerGroup, topic, offset);
        
        // Then
        // Verify by polling - should only get messages after offset 5
        MessageBatch batch = broker.pollMessages(consumerGroup, 10);
        assertThat(batch.getMessages()).isEmpty(); // No messages after offset 5
    }
    
    @Test
    @DisplayName("Should only return messages after committed offset")
    void shouldOnlyReturnMessagesAfterCommittedOffset() throws Exception {
        // Given
        String consumerGroup = "test-group";
        String topic = "test-topic";
        
        broker.subscribe(consumerGroup, List.of(topic));
        
        // Publish 5 messages (offsets 1-5)
        for (int i = 0; i < 5; i++) {
            Message message = Message.create(topic, "Event" + i, "payload" + i, new HashMap<>(), "key" + i);
            broker.publish(message).get();
        }
        
        // Commit offset 3
        broker.commitOffset(consumerGroup, topic, 3L);
        
        // When - poll messages
        MessageBatch batch = broker.pollMessages(consumerGroup, 10);
        
        // Then - should only get messages with offset > 3 (i.e., offsets 4 and 5)
        assertThat(batch.getMessages()).hasSize(2);
        assertThat(batch.getMessages().get(0).getOffset()).isEqualTo(4L);
        assertThat(batch.getMessages().get(1).getOffset()).isEqualTo(5L);
    }
    
    @Test
    @DisplayName("Should return broker statistics")
    void shouldReturnBrokerStatistics() throws Exception {
        // Given
        broker.subscribe("group1", List.of("topic1"));
        broker.subscribe("group2", List.of("topic2"));
        
        // Publish some messages
        Message message1 = Message.create("topic1", "Event1", "payload1", new HashMap<>(), "key1");
        Message message2 = Message.create("topic2", "Event2", "payload2", new HashMap<>(), "key2");
        
        broker.publish(message1).get();
        broker.publish(message2).get();
        
        // When
        InMemoryMessageBroker.BrokerStats stats = broker.getStats();
        
        // Then
        assertThat(stats.totalTopics()).isEqualTo(2);
        assertThat(stats.totalConsumerGroups()).isEqualTo(2);
        assertThat(stats.totalMessages()).isEqualTo(2L);
    }
    
    @Test
    @DisplayName("Should handle concurrent publishing")
    void shouldHandleConcurrentPublishing() throws Exception {
        // Given
        String topic = "concurrent-topic";
        int numberOfThreads = 10;
        int messagesPerThread = 100;
        
        // When - publish messages concurrently
        List<CompletableFuture<Message>> futures = new java.util.ArrayList<>();
        
        for (int i = 0; i < numberOfThreads; i++) {
            for (int j = 0; j < messagesPerThread; j++) {
                Message message = Message.create(topic, "Event", "payload", new HashMap<>(), "key");
                futures.add(broker.publish(message));
            }
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        
        // Then
        InMemoryMessageBroker.BrokerStats stats = broker.getStats();
        assertThat(stats.totalMessages()).isEqualTo(numberOfThreads * messagesPerThread);
    }
}
