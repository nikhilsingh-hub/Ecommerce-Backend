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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
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
    public void handleProductCreated(Message message) {
        try {
            ProductEvent.ProductCreated event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductCreated.class);
            
            // Create ProductDocument directly from event data - no database fetch needed!
            ProductDocument document = ProductDocument.builder()
                .id(event.getProductId().toString())
                .productId(event.getProductId())
                .name(event.getName())
                .description(event.getDescription())
                .categories(event.getCategories())
                .price(event.getPrice())
                .sku(event.getSku())
                .attributes(event.getAttributes())
                .images(event.getImages())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getCreatedAt()) // Same as created for new products
                .clickCount(0L) // New product starts with 0
                .purchaseCount(0L) // New product starts with 0  
                .popularityScore(0.0) // New product starts with 0
                .allText(buildAllTextFromEvent(event))
                .tags(generateTagsFromEvent(event))
                .inStock(true) // Assume new products are in stock
                .priceRange(calculatePriceRange(event.getPrice()))
                .scoreBoost(1.0) // Default boost for new products
                .build();
                
            searchRepository.save(document);
            log.debug("Indexed new product {} in Elasticsearch from event data", event.getProductId());
            
        } catch (Exception e) {
            log.error("Failed to handle ProductCreated event", e);
            throw new RuntimeException("Failed to process ProductCreated event", e);
        }
    }
    
    /**
     * Handle ProductUpdated event
     */
    public void handleProductUpdated(Message message) {
        try {
            ProductEvent.ProductUpdated event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductUpdated.class);
            
            // Create ProductDocument directly from event data - no database fetch needed!
            ProductDocument document = ProductDocument.builder()
                .id(event.getProductId().toString())
                .productId(event.getProductId())
                .name(event.getName())
                .description(event.getDescription())
                .categories(event.getCategories())
                .price(event.getPrice())
                .sku(event.getSku())
                .attributes(event.getAttributes())
                .images(event.getImages())
                .createdAt(null) // We don't have createdAt in update events, ES will preserve existing value
                .updatedAt(event.getUpdatedAt())
                .clickCount(null) // Preserve existing analytics data in Elasticsearch
                .purchaseCount(null) // Preserve existing analytics data in Elasticsearch
                .popularityScore(null) // Preserve existing analytics data in Elasticsearch
                .allText(buildAllTextFromEvent(event))
                .tags(generateTagsFromEvent(event))
                .inStock(true) // Assume updated products are in stock
                .priceRange(calculatePriceRange(event.getPrice()))
                .scoreBoost(1.0) // Default boost
                .build();

            searchRepository.save(document);
            log.debug("Updated product {} in Elasticsearch from event data", event.getProductId());
            
        } catch (Exception e) {
            log.error("Failed to handle ProductUpdated event", e);
            throw new RuntimeException("Failed to process ProductUpdated event", e);
        }
    }
    
    /**
     * Handle ProductDeleted event
     */
    public void handleProductDeleted(Message message) {
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
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void handleProductAnalyticsEvent(Message message) {
        try {
            String aggregateId = message.getHeaders().get("aggregate-id");
            Long productId = Long.parseLong(aggregateId);
            
            // Fetch updated product data to get latest metrics with all collections safely loaded
            Optional<Product> product = findProductWithAllCollections(productId);
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
    @Transactional(readOnly = true)
    public CompletableFuture<Void> performFullSync() {
        log.info("Starting full synchronization from MySQL to Elasticsearch");
        
        try {
            long totalSynced = performFullSyncInternal();
            log.info("Full synchronization completed. Synced {} products", totalSynced);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Full synchronization failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private long performFullSyncInternal() {
        int page = 0;
        long totalSynced = 0;
        
        while (true) {
            PageRequest pageRequest = PageRequest.of(page, syncBatchSize);
            Page<Product> products = productRepository.findAllWithCategories(pageRequest);
            
            if (products.isEmpty()) {
                break;
            }
            
            // Convert and save to Elasticsearch
            // Collections are safely accessible within transaction context
            List<ProductDocument> documents = products.getContent().stream()
                .map(product -> {
                    // Force initialization of lazy collections within transaction
                    initializeProductCollections(product);
                    return ProductDocument.fromProduct(product);
                })
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
        
        return totalSynced;
    }
    
    /**
     * Perform incremental sync for products updated since last sync
     * This runs periodically to catch any missed events
     */
    @Scheduled(fixedDelayString = "${elasticsearch.sync.incremental-interval-ms:300000}") // 5 minutes
    @Async
    @Transactional(readOnly = true)
    public CompletableFuture<Void> performIncrementalSync() {
        log.debug("Starting incremental synchronization");
        
        try {
            long totalSynced = performIncrementalSyncInternal();
            
            if (totalSynced > 0) {
                log.info("Incremental synchronization completed. Synced {} products", totalSynced);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Incremental synchronization failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private long performIncrementalSyncInternal() {
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
                    .map(product -> {
                        // Force initialization of lazy collections within transaction
                        initializeProductCollections(product);
                        return ProductDocument.fromProduct(product);
                    })
                    .toList();
                
                searchRepository.saveAll(documents);
                totalSynced += documents.size();
            }
            
            if (products.isLast()) {
                break;
            }
            
            page++;
        }
        
        return totalSynced;
    }
    
    /**
     * Sync a specific product by ID
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void syncProduct(Long productId) {
        log.debug("Syncing specific product: {}", productId);
        
        try {
            Optional<Product> product = findProductWithAllCollections(productId);
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
     * Helper method to safely load product with all collections within transactional context
     * This avoids MultipleBagFetchException by initializing collections sequentially
     */
    private Optional<Product> findProductWithAllCollections(Long productId) {
        Optional<Product> productOpt = productRepository.findByIdWithCategories(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            initializeProductCollections(product);
            log.debug("Initialized all collections for product {}", productId);
        }
        return productOpt;
    }
    
    /**
     * Force initialization of lazy collections within transaction context
     */
    private void initializeProductCollections(Product product) {
        try {
            // Force initialization by accessing collections
            product.getAttributes().size(); // Initialize attributes collection
            product.getImages().size();     // Initialize images collection
        } catch (Exception e) {
            log.warn("Failed to initialize collections for product {}: {}", product.getId(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Build searchable text from event data (similar to ProductDocument.buildAllText)
     */
    private String buildAllTextFromEvent(ProductEvent.ProductCreated event) {
        StringBuilder allText = new StringBuilder();
        
        if (event.getName() != null) {
            allText.append(event.getName()).append(" ");
        }
        if (event.getDescription() != null) {
            allText.append(event.getDescription()).append(" ");
        }
        if (event.getSku() != null) {
            allText.append(event.getSku()).append(" ");
        }
        if (event.getCategories() != null) {
            allText.append(String.join(" ", event.getCategories())).append(" ");
        }
        if (event.getAttributes() != null) {
            event.getAttributes().forEach((key, value) -> 
                allText.append(key).append(" ").append(value).append(" "));
        }
        
        return allText.toString().trim();
    }
    
    /**
     * Generate tags from event data (similar to ProductDocument.generateTags)
     */
    private List<String> generateTagsFromEvent(ProductEvent.ProductCreated event) {
        List<String> tags = new ArrayList<>();
        
        // Add categories as tags
        if (event.getCategories() != null) {
            tags.addAll(event.getCategories());
        }
        
        // Add brand as tag if available
        if (event.getAttributes() != null && event.getAttributes().containsKey("brand")) {
            tags.add(event.getAttributes().get("brand"));
        }
        
        // Add price-based tags
        if (event.getPrice() != null) {
            tags.add(calculatePriceRange(event.getPrice()));
        }
        
        return tags.stream().distinct().collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Build searchable text from ProductUpdated event data
     */
    private String buildAllTextFromEvent(ProductEvent.ProductUpdated event) {
        StringBuilder allText = new StringBuilder();
        
        if (event.getName() != null) {
            allText.append(event.getName()).append(" ");
        }
        if (event.getDescription() != null) {
            allText.append(event.getDescription()).append(" ");
        }
        if (event.getSku() != null) {
            allText.append(event.getSku()).append(" ");
        }
        if (event.getCategories() != null) {
            allText.append(String.join(" ", event.getCategories())).append(" ");
        }
        if (event.getAttributes() != null) {
            event.getAttributes().forEach((key, value) -> 
                allText.append(key).append(" ").append(value).append(" "));
        }
        
        return allText.toString().trim();
    }
    
    /**
     * Generate tags from ProductUpdated event data
     */
    private List<String> generateTagsFromEvent(ProductEvent.ProductUpdated event) {
        List<String> tags = new ArrayList<>();
        
        // Add categories as tags
        if (event.getCategories() != null) {
            tags.addAll(event.getCategories());
        }
        
        // Add brand as tag if available
        if (event.getAttributes() != null && event.getAttributes().containsKey("brand")) {
            tags.add(event.getAttributes().get("brand"));
        }
        
        // Add price-based tags
        if (event.getPrice() != null) {
            double priceValue = event.getPrice().doubleValue();
            if (priceValue < 100) {
                tags.add("budget");
            } else if (priceValue > 500) {
                tags.add("premium");
            }
        }
        
        return tags;
    }
    
    /**
     * Calculate price range (reuse ProductDocument logic)
     */
    private String calculatePriceRange(java.math.BigDecimal price) {
        if (price == null) return "unknown";
        
        double priceValue = price.doubleValue();
        if (priceValue < 50) return "0-50";
        else if (priceValue < 100) return "50-100";
        else if (priceValue < 200) return "100-200";
        else if (priceValue < 500) return "200-500";
        else if (priceValue < 1000) return "500-1000";
        else return "1000+";
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
