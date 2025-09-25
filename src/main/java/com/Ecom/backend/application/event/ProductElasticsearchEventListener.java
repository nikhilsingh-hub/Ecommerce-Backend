package com.Ecom.backend.application.event;

import com.Ecom.backend.application.dto.event.ProductEvent;
import com.Ecom.backend.application.service.ProductElasticsearchSyncService;
import com.Ecom.backend.infrastructure.elasticsearch.ProductDocument;
import com.Ecom.backend.infrastructure.elasticsearch.ProductSearchRepository;
import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumer;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event listener for product-related events that syncs data to Elasticsearch
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductElasticsearchEventListener implements BaseEventListener {
    
    private final ProductSearchRepository searchRepository;
    private final ObjectMapper objectMapper;
    public final MessageConsumerFactory consumerFactory;
    private final ProductElasticsearchSyncService syncService;
    
    @Value("${elasticsearch.sync.consumer-workers:2}")
    private int elasticsearchWorkers;

    private List<MessageConsumer> consumers;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeConsumers() {
        log.info("Initializing Elasticsearch sync consumers");

        // Create consumer group for product events
        consumers = consumerFactory.createConsumerGroup(
                "elasticsearch-sync",
                getSubscribedTopics(),
                elasticsearchWorkers,
                this::handleMessage
        );

        // Start consuming
        consumerFactory.startConsumerGroup("elasticsearch-sync");

        log.info("Elasticsearch sync consumers started with {} workers", elasticsearchWorkers);
    }

    public void handleMessage(Message message) {
        log.debug("Processing product event: {}", message.getEventType());

        try {
            // Check idempotency
            String idempotencyKey = message.getHeaders().get("idempotency-key");
            if (isAlreadyProcessed(idempotencyKey)) {
                log.debug("Event {} already processed, skipping", idempotencyKey);
                return;
            }

            // Process based on event type
            switch (message.getEventType()) {
                case "ProductCreated" -> syncService.handleProductCreated(message);
                case "ProductUpdated" -> syncService.handleProductUpdated(message);
                case "ProductDeleted" -> syncService.handleProductDeleted(message);
                case "ProductViewed", "ProductPurchased" -> syncService.handleProductAnalyticsEvent(message);
                default -> log.warn("Unknown event type: {}", message.getEventType());
            }

            // Mark as processed (in real implementation, store in cache/database)
            markAsProcessed(idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process product event {}: {}", message.getId(), e.getMessage(), e);
            throw new RuntimeException("Event processing failed", e);
        }
    }

    public void shutdown() {

    }


    public List<MessageConsumer> createConsumers() {
        return List.of();
    }


    public List<String> getSubscribedTopics() {
        return List.of("product-events");
    }
    

    private boolean isAlreadyProcessed(String idempotencyKey) {
        return false; // its POC, so treating every key as unique - can use redis here
    }
    
    private void markAsProcessed(String idempotencyKey) {
        // Implement marking as processed
    }
}
