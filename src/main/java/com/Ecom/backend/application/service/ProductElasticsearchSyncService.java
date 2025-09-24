package com.Ecom.backend.application.service;

import com.Ecom.backend.application.dto.event.ProductEvent;
import com.Ecom.backend.domain.entity.Product;
import com.Ecom.backend.infrastructure.elasticsearch.ProductDocument;
import com.Ecom.backend.infrastructure.elasticsearch.ProductSearchRepository;
import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumer;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumerFactory;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for synchronizing product data between MySQL and Elasticsearch.
 * 
 * This service:
 * 1. Consumes product events from the pub/sub system
 * 2. Updates Elasticsearch index based on MySQL changes
 * 3. Provides periodic full sync capabilities
 * 4. Handles idempotency to prevent duplicate updates
 * 
 * It demonstrates eventual consistency and the CQRS pattern.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductElasticsearchSyncService {
    
    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final MessageConsumerFactory consumerFactory;
    private final ObjectMapper objectMapper;
    
    @Value("${elasticsearch.sync.batch-size:100}")
    private int syncBatchSize;
    
    @Value("${elasticsearch.sync.consumer-workers:2}")
    private int consumerWorkers;
    
    private List<MessageConsumer> consumers;
    
    /**
     * Initialize consumers after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeConsumers() {
        log.info("Initializing Elasticsearch sync consumers");
        
        // Create consumer group for product events
        consumers = consumerFactory.createConsumerGroup(
            "elasticsearch-sync",
            List.of("product-events"),
            consumerWorkers,
            this::handleProductEvent
        );
        
        // Start consuming
        consumerFactory.startConsumerGroup("elasticsearch-sync");
        
        log.info("Elasticsearch sync consumers started with {} workers", consumerWorkers);
    }
    
    /**
     * Handle product events from the pub/sub system
     */
    public void handleProductEvent(Message message) {
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
                case "ProductCreated" -> handleProductCreated(message);
                case "ProductUpdated" -> handleProductUpdated(message);
                case "ProductDeleted" -> handleProductDeleted(message);
                case "ProductViewed", "ProductPurchased" -> handleProductAnalyticsEvent(message);
                default -> log.warn("Unknown event type: {}", message.getEventType());
            }
            
            // Mark as processed (in real implementation, store in cache/database)
            markAsProcessed(idempotencyKey);
            
        } catch (Exception e) {
            log.error("Failed to process product event {}: {}", message.getId(), e.getMessage(), e);
            throw new RuntimeException("Event processing failed", e);
        }
    }
    
    /**
     * Handle ProductCreated event
     */
    private void  handleProductCreated(Message message) {
        try {
            ProductEvent.ProductCreated event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductCreated.class);
            
            // Fetch full product data from MySQL with eager loaded categories
            Optional<Product> product = productRepository.findByIdWithCategories(event.getProductId());
            if (product.isPresent()) {
                ProductDocument document = ProductDocument.fromProduct(product.get());
                searchRepository.save(document);
                log.debug("Indexed new product {} in Elasticsearch", event.getProductId());
            } else {
                log.warn("Product {} not found in MySQL for indexing", event.getProductId());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle ProductCreated event", e);
            throw new RuntimeException("Failed to process ProductCreated event", e);
        }
    }
    
    /**
     * Handle ProductUpdated event
     */
    private void handleProductUpdated(Message message) {
        try {
            ProductEvent.ProductUpdated event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductUpdated.class);
            
            // Fetch updated product data from MySQL with eager loaded categories
            Optional<Product> product = productRepository.findByIdWithCategories(event.getProductId());
            if (product.isPresent()) {
                ProductDocument document = ProductDocument.fromProduct(product.get());
                searchRepository.save(document);
                log.debug("Updated product {} in Elasticsearch", event.getProductId());
            } else {
                log.warn("Product {} not found in MySQL for update", event.getProductId());
                // Product might have been deleted, remove from Elasticsearch
                searchRepository.deleteById(event.getProductId().toString());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle ProductUpdated event", e);
            throw new RuntimeException("Failed to process ProductUpdated event", e);
        }
    }
    
    /**
     * Handle ProductDeleted event
     */
    private void handleProductDeleted(Message message) {
        try {
            ProductEvent.ProductDeleted event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductDeleted.class);
            
            searchRepository.deleteById(event.getProductId().toString());
            log.debug("Deleted product {} from Elasticsearch", event.getProductId());
            
        } catch (Exception e) {
            log.error("Failed to handle ProductDeleted event", e);
            throw new RuntimeException("Failed to process ProductDeleted event", e);
        }
    }
    
    /**
     * Handle analytics events (view, purchase) to update metrics
     */
    private void handleProductAnalyticsEvent(Message message) {
        try {
            String aggregateId = message.getHeaders().get("aggregate-id");
            Long productId = Long.parseLong(aggregateId);
            
            // Fetch updated product data to get latest metrics with eager loaded categories
            Optional<Product> product = productRepository.findByIdWithCategories(productId);
            if (product.isPresent()) {
                ProductDocument document = ProductDocument.fromProduct(product.get());
                searchRepository.save(document);
                log.debug("Updated product {} metrics in Elasticsearch", productId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle analytics event", e);
            throw new RuntimeException("Failed to process analytics event", e);
        }
    }
    
    /**
     * Perform full synchronization from MySQL to Elasticsearch
     * This is useful for initial data loading or recovery scenarios
     */
    @Async
    public CompletableFuture<Void> performFullSync() {
        log.info("Starting full synchronization from MySQL to Elasticsearch");
        
        try {
            int page = 0;
            long totalSynced = 0;
            
            while (true) {
                PageRequest pageRequest = PageRequest.of(page, syncBatchSize);
                Page<Product> products = productRepository.findAllWithCategories(pageRequest);
                
                if (products.isEmpty()) {
                    break;
                }
                
                // Convert and save to Elasticsearch
                List<ProductDocument> documents = products.getContent().stream()
                    .map(ProductDocument::fromProduct)
                    .toList();
                
                searchRepository.saveAll(documents);
                totalSynced += documents.size();
                
                log.debug("Synced batch {} with {} products (total: {})", 
                    page + 1, documents.size(), totalSynced);
                
                if (products.isLast()) {
                    break;
                }
                
                page++;
            }
            
            log.info("Full synchronization completed. Synced {} products", totalSynced);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Full synchronization failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Perform incremental sync for products updated since last sync
     * This runs periodically to catch any missed events
     */
    @Scheduled(fixedDelayString = "${elasticsearch.sync.incremental-interval-ms:300000}") // 5 minutes
    @Async
    public CompletableFuture<Void> performIncrementalSync() {
        log.debug("Starting incremental synchronization");
        
        try {
            // Find products updated in the last hour (to be safe)
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusHours(1);
            
            int page = 0;
            long totalSynced = 0;
            
            while (true) {
                PageRequest pageRequest = PageRequest.of(page, syncBatchSize);
                
                // Use JOIN FETCH to eagerly load categories
                Page<Product> products = productRepository.findAllWithCategories(pageRequest);
                
                if (products.isEmpty()) {
                    break;
                }
                
                // Filter products updated since the last sync
                List<Product> recentlyUpdated = products.getContent().stream()
                    .filter(product -> product.getUpdatedAt().isAfter(since))
                    .toList();
                
                if (!recentlyUpdated.isEmpty()) {
                    List<ProductDocument> documents = recentlyUpdated.stream()
                        .map(ProductDocument::fromProduct)
                        .toList();
                    
                    searchRepository.saveAll(documents);
                    totalSynced += documents.size();
                }
                
                if (products.isLast()) {
                    break;
                }
                
                page++;
            }
            
            if (totalSynced > 0) {
                log.info("Incremental synchronization completed. Synced {} products", totalSynced);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Incremental synchronization failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Sync a specific product by ID
     */
    public void syncProduct(Long productId) {
        log.debug("Syncing specific product: {}", productId);
        
        try {
            Optional<Product> product = productRepository.findByIdWithCategories(productId);
            if (product.isPresent()) {
                ProductDocument document = ProductDocument.fromProduct(product.get());
                searchRepository.save(document);
                log.debug("Synced product {} to Elasticsearch", productId);
            } else {
                // Product was deleted, remove from Elasticsearch
                searchRepository.deleteById(productId.toString());
                log.debug("Removed deleted product {} from Elasticsearch", productId);
            }
            
        } catch (Exception e) {
            log.error("Failed to sync product {}", productId, e);
            throw new RuntimeException("Failed to sync product", e);
        }
    }
    
    /**
     * Get synchronization statistics
     */
    public SyncStats getSyncStats() {
        try {
            long mysqlCount = productRepository.count();
            long elasticsearchCount = searchRepository.count();
            
            return new SyncStats(mysqlCount, elasticsearchCount, mysqlCount == elasticsearchCount);
            
        } catch (Exception e) {
            log.error("Failed to get sync statistics", e);
            return new SyncStats(0, 0, false);
        }
    }
    
    /**
     * Simple idempotency check (in production, use Redis or database)
     */
    private boolean isAlreadyProcessed(String idempotencyKey) {
        // For demo purposes, assume not processed
        // In production, check cache or database
        return false;
    }
    
    /**
     * Mark event as processed (in production, store in Redis or database)
     */
    private void markAsProcessed(String idempotencyKey) {
        // For demo purposes, do nothing
        // In production, store in cache or database with TTL
    }
    
    /**
     * Cleanup resources
     */
    @PreDestroy
    public void cleanup() {
        if (consumers != null) {
            consumers.forEach(MessageConsumer::close);
        }
        log.info("Elasticsearch sync service cleanup completed");
    }
    
    /**
     * Synchronization statistics
     */
    public record SyncStats(
        long mysqlProductCount,
        long elasticsearchProductCount,
        boolean inSync
    ) {}
}
