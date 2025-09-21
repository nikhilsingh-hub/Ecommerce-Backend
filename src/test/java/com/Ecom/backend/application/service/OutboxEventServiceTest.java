package com.Ecom.backend.application.service;

import com.Ecom.backend.domain.entity.OutboxEvent;
import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessagePublisher;
import com.Ecom.backend.infrastructure.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OutboxEventService.
 * Tests outbox pattern implementation, retry logic, and idempotency.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventService Tests")
class OutboxEventServiceTest {
    
    @Mock
    private OutboxEventRepository outboxEventRepository;
    
    @Mock
    private MessagePublisher messagePublisher;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private OutboxEventService outboxEventService;
    
    private OutboxEvent testEvent;
    
    @BeforeEach
    void setUp() {
        testEvent = OutboxEvent.builder()
            .id(1L)
            .aggregateId("123")
            .aggregateType("Product")
            .eventType("ProductCreated")
            .eventData("{\"productId\": 123}")
            .processed(false)
            .retryCount(0)
            .build();
    }
    
    @Test
    @DisplayName("Should store event in outbox")
    void shouldStoreEventInOutbox() throws Exception {
        // Given
        String aggregateId = "123";
        String aggregateType = "Product";
        String eventType = "ProductCreated";
        Object eventData = new TestEventData("test");
        
        when(objectMapper.writeValueAsString(eventData)).thenReturn("{\"data\":\"test\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(testEvent);
        
        // When
        OutboxEvent result = outboxEventService.storeEvent(aggregateId, aggregateType, eventType, eventData);
        
        // Then
        assertThat(result).isEqualTo(testEvent);
        
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getAggregateId()).isEqualTo(aggregateId);
        assertThat(savedEvent.getAggregateType()).isEqualTo(aggregateType);
        assertThat(savedEvent.getEventType()).isEqualTo(eventType);
        assertThat(savedEvent.getEventData()).isEqualTo("{\"data\":\"test\"}");
        assertThat(savedEvent.getProcessed()).isFalse();
    }
    
    @Test
    @DisplayName("Should process unprocessed events")
    void shouldProcessUnprocessedEvents() {
        // Given
        List<OutboxEvent> unprocessedEvents = List.of(testEvent);
        when(outboxEventRepository.findUnprocessedEvents(any(PageRequest.class)))
            .thenReturn(unprocessedEvents);
        when(messagePublisher.publish(any(Message.class)))
            .thenReturn(CompletableFuture.completedFuture(createTestMessage()));
        
        // When
        outboxEventService.processOutboxEvents();
        
        // Then
        verify(messagePublisher).publish(any(Message.class));
        verify(outboxEventRepository).markAsProcessed(eq(1L), any(LocalDateTime.class));
    }
    
    @Test
    @DisplayName("Should process events eligible for retry")
    void shouldProcessEventsEligibleForRetry() {
        // Given
        testEvent.setRetryCount(2);
        testEvent.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        
        List<OutboxEvent> retryableEvents = List.of(testEvent);
        when(outboxEventRepository.findEventsForRetry(any(LocalDateTime.class), any(PageRequest.class)))
            .thenReturn(retryableEvents);
        when(messagePublisher.publish(any(Message.class)))
            .thenReturn(CompletableFuture.completedFuture(createTestMessage()));
        
        // When
        outboxEventService.processRetryableEvents();
        
        // Then
        verify(messagePublisher).publish(any(Message.class));
        verify(outboxEventRepository).markAsProcessed(eq(1L), any(LocalDateTime.class));
    }
    
    @Test
    @DisplayName("Should handle event processing failure")
    void shouldHandleEventProcessingFailure() {
        // Given
        List<OutboxEvent> unprocessedEvents = List.of(testEvent);
        when(outboxEventRepository.findUnprocessedEvents(any(PageRequest.class)))
            .thenReturn(unprocessedEvents);
        
        RuntimeException publishFailure = new RuntimeException("Publishing failed");
        when(messagePublisher.publish(any(Message.class)))
            .thenReturn(CompletableFuture.failedFuture(publishFailure));
        
        // When
        outboxEventService.processOutboxEvents();
        
        // Then
        verify(messagePublisher).publish(any(Message.class));
        verify(outboxEventRepository).incrementRetryCount(
            eq(1L), any(LocalDateTime.class), eq("Publishing failed"));
        verify(outboxEventRepository, never()).markAsProcessed(any(), any());
    }
    
    @Test
    @DisplayName("Should create proper message headers")
    void shouldCreateProperMessageHeaders() {
        // Given
        List<OutboxEvent> unprocessedEvents = List.of(testEvent);
        when(outboxEventRepository.findUnprocessedEvents(any(PageRequest.class)))
            .thenReturn(unprocessedEvents);
        
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        when(messagePublisher.publish(messageCaptor.capture()))
            .thenReturn(CompletableFuture.completedFuture(createTestMessage()));
        
        // When
        outboxEventService.processOutboxEvents();
        
        // Then
        Message publishedMessage = messageCaptor.getValue();
        assertThat(publishedMessage.getTopic()).isEqualTo("product-events");
        assertThat(publishedMessage.getEventType()).isEqualTo("ProductCreated");
        assertThat(publishedMessage.getPayload()).isEqualTo("{\"productId\": 123}");
        assertThat(publishedMessage.getPartitionKey()).isEqualTo("123");
        
        // Verify headers
        assertThat(publishedMessage.getHeaders())
            .containsEntry("idempotency-key", "outbox-event-1")
            .containsEntry("aggregate-id", "123")
            .containsEntry("aggregate-type", "Product")
            .containsEntry("event-type", "ProductCreated")
            .containsEntry("source", "outbox-service");
    }
    
    @Test
    @DisplayName("Should determine correct topic based on aggregate type")
    void shouldDetermineCorrectTopicBasedOnAggregateType() {
        // Given - Product event
        OutboxEvent productEvent = OutboxEvent.builder()
            .aggregateType("Product")
            .build();
        
        OutboxEvent orderEvent = OutboxEvent.builder()
            .aggregateType("Order")
            .build();
        
        OutboxEvent unknownEvent = OutboxEvent.builder()
            .aggregateType("Unknown")
            .build();
        
        List<OutboxEvent> events = List.of(productEvent, orderEvent, unknownEvent);
        when(outboxEventRepository.findUnprocessedEvents(any(PageRequest.class)))
            .thenReturn(events);
        
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        when(messagePublisher.publish(messageCaptor.capture()))
            .thenReturn(CompletableFuture.completedFuture(createTestMessage()));
        
        // When
        outboxEventService.processOutboxEvents();
        
        // Then
        List<Message> publishedMessages = messageCaptor.getAllValues();
        assertThat(publishedMessages).hasSize(3);
        assertThat(publishedMessages.get(0).getTopic()).isEqualTo("product-events");
        assertThat(publishedMessages.get(1).getTopic()).isEqualTo("order-events");
        assertThat(publishedMessages.get(2).getTopic()).isEqualTo("general-events");
    }
    
    @Test
    @DisplayName("Should delete old processed events")
    void shouldDeleteOldProcessedEvents() {
        // Given
        int deletedCount = 42;
        when(outboxEventRepository.deleteOldProcessedEvents(any(LocalDateTime.class)))
            .thenReturn(deletedCount);
        
        // When
        outboxEventService.cleanupOldEvents();
        
        // Then
        ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventRepository).deleteOldProcessedEvents(dateCaptor.capture());
        
        LocalDateTime cutoffDate = dateCaptor.getValue();
        assertThat(cutoffDate).isBefore(LocalDateTime.now());
        // Should be approximately 7 days ago (default cleanup period)
        assertThat(cutoffDate).isAfter(LocalDateTime.now().minusDays(8));
    }
    
    @Test
    @DisplayName("Should get outbox statistics")
    void shouldGetOutboxStatistics() {
        // Given
        when(outboxEventRepository.countUnprocessedEvents()).thenReturn(15L);
        when(outboxEventRepository.getEventStatusStatistics())
            .thenReturn(List.<Object[]>of(new Object[]{"processed", 3, 100L}));
        
        // When
        OutboxEventService.OutboxStats stats = outboxEventService.getStats();
        
        // Then
        assertThat(stats.unprocessedEventCount()).isEqualTo(15L);
        assertThat(stats.totalEventTypes()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Should handle concurrent event processing")
    void shouldHandleConcurrentEventProcessing() {
        // Given
        OutboxEvent event1 = OutboxEvent.builder().id(1L).build();
        OutboxEvent event2 = OutboxEvent.builder().id(2L).build();
        
        List<OutboxEvent> unprocessedEvents = List.of(event1, event2);
        when(outboxEventRepository.findUnprocessedEvents(any(PageRequest.class)))
            .thenReturn(unprocessedEvents);
        when(messagePublisher.publish(any(Message.class)))
            .thenReturn(CompletableFuture.completedFuture(createTestMessage()));
        
        // When
        outboxEventService.processOutboxEvents();
        
        // Then - both events should be processed
        verify(messagePublisher, times(2)).publish(any(Message.class));
        verify(outboxEventRepository).markAsProcessed(eq(1L), any(LocalDateTime.class));
        verify(outboxEventRepository).markAsProcessed(eq(2L), any(LocalDateTime.class));
    }
    
    private Message createTestMessage() {
        return Message.builder()
            .id("test-message-id")
            .topic("test-topic")
            .eventType("TestEvent")
            .payload("test payload")
            .build();
    }
    
    private static class TestEventData {
        private final String data;
        
        public TestEventData(String data) {
            this.data = data;
        }
        
        public String getData() {
            return data;
        }
    }
}
