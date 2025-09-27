package com.Ecom.backend.application.service.ProductService;

import com.Ecom.backend.application.dto.event.ProductEvent;
import com.Ecom.backend.application.dto.event.ProductEventType;
import com.Ecom.backend.domain.entity.Product;
import com.Ecom.backend.infrastructure.elasticsearch.ProductDocument;
import com.Ecom.backend.infrastructure.elasticsearch.ProductSearchRepository;
import com.Ecom.backend.infrastructure.pubsub.DTO.Message;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    
    @Value("${elasticsearch.sync.batch-size:100}")
    private int syncBatchSize;

    /**
     * Handle product events from the pub/sub system
     */
    public void handleProductEvent(Message message) {
        log.debug("Processing product event: {}", message.getEventType());

        try {

            String idempotencyKey = message.getHeaders().get("idempotency-key");
            if (isAlreadyProcessed(idempotencyKey)) {
                log.debug("Event {} already processed, skipping", idempotencyKey);
                return;
            }

            // Process based on event type
            switch (ProductEventType.fromString(message.getEventType())) {
                case PRODUCT_CREATED -> handleProductCreated(message);
                case PRODUCT_UPDATED -> handleProductUpdated(message);
                case PRODUCT_DELETED -> handleProductDeleted(message);
                case PRODUCT_VIEWED, PRODUCT_PURCHASED -> handleProductAnalyticsEvent(message);
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
            ProductEvent.ProductCreated event = objectMapper.readValue(message.getPayload(), ProductEvent.ProductCreated.class);

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
                .updatedAt(event.getCreatedAt())
                .clickCount(0L)
                .purchaseCount(0L)
                .popularityScore(0.0)
                .allText(buildAllTextFromEvent(event))
                .tags(generateTagsFromEvent(event))
                .inStock(true)
                .priceRange(calculatePriceRange(event.getPrice()))
                .scoreBoost(1.0)
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
                .createdAt(null)
                .updatedAt(event.getUpdatedAt())
                .clickCount(null)
                .purchaseCount(null)
                .popularityScore(null)
                .allText(buildAllTextFromEvent(event))
                .tags(generateTagsFromEvent(event))
                .inStock(true)
                .priceRange(calculatePriceRange(event.getPrice()))
                .scoreBoost(1.0)
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
            ProductEvent.ProductDeleted event = objectMapper.readValue(message.getPayload(), ProductEvent.ProductDeleted.class);

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
    public void handleProductAnalyticsEvent(Message message) {
        try {
            String aggregateId = message.getHeaders().get("aggregate-id");
            String eventType = message.getEventType();
            Long productId = Long.parseLong(aggregateId);

            Map<String, Object> metricsUpdate = new HashMap<>();
            
            if ("ProductViewed".equals(eventType)) {
                Long incrementValue = extractViewIncrement(message);
                Long totalViews = extractTotalViews(message);
                
                if (totalViews != null) {
                    metricsUpdate.put("clickCount", totalViews);
                } else {
                    metricsUpdate.put("clickCount", incrementValue);
                    log.debug("Incrementing click count for product {} by {}", productId, incrementValue);
                }

                Double popularityScore = extractPopularityScore(message);
                if (popularityScore != null) {
                    metricsUpdate.put("popularityScore", popularityScore);
                }
                
            } else if ("ProductPurchased".equals(eventType)) {
                // Handle purchase events - increment purchase count
                Long incrementValue = extractPurchaseIncrement(message);
                metricsUpdate.put("purchaseCount", incrementValue);
                log.debug("Incrementing purchase count for product {} by {}", productId, incrementValue);
                
            } else {
                log.debug("Unknown analytics event type: {}", eventType);
                return;
            }
            
            // Use script-based update to increment counters atomically
            updateProductMetrics(productId.toString(), metricsUpdate);
            log.debug("Updated product {} {} metrics in Elasticsearch", productId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to handle analytics event", e);
            throw new RuntimeException("Failed to process analytics event", e);
        }
    }
    
    /**
     * Update product metrics using TRUE server-side atomic increments
     */
    private void updateProductMetrics(String productId, Map<String, Object> updates) {
        try {
            StringBuilder script = new StringBuilder();
            Map<String, Object> scriptParams = new HashMap<>();
            
            if (updates.containsKey("clickCount")) {
                Long clickCountValue = ((Number) updates.get("clickCount")).longValue();
                scriptParams.put("clickCount", clickCountValue);
                
                if (updates.containsKey("total_views")) {
                    script.append("ctx._source.clickCount = params.clickCount; ");
                } else {
                    script.append("ctx._source.clickCount = (ctx._source.clickCount ?: 0) + params.clickCount; ");
                }
            }
            
            if (updates.containsKey("purchaseCount")) {
                Long purchaseCountValue = ((Number) updates.get("purchaseCount")).longValue();
                scriptParams.put("purchaseCount", purchaseCountValue);
                script.append("ctx._source.purchaseCount = (ctx._source.purchaseCount ?: 0) + params.purchaseCount; ");
            }
            
            if (updates.containsKey("popularityScore")) {
                Double popularityValue = ((Number) updates.get("popularityScore")).doubleValue();
                scriptParams.put("popularityScore", popularityValue);
                script.append("ctx._source.popularityScore = params.popularityScore; ");
            } else {
                script.append("long clicks = ctx._source.clickCount ?: 0; ");
                script.append("long purchases = ctx._source.purchaseCount ?: 0; ");
                script.append("ctx._source.popularityScore = (clicks * 1.0) + (purchases * 10.0);");
            }

            UpdateQuery updateQuery = UpdateQuery.builder(productId)
                .withScript(script.toString())
                .withParams(scriptParams)
                .withRetryOnConflict(3)
                .build();
            
            IndexCoordinates index = IndexCoordinates.of("products");
            elasticsearchOperations.update(updateQuery, index);
            log.debug("Server-side atomic update completed for product {} - updates: {}", productId, updates.keySet());
            
        } catch (Exception e) {
            log.error("Failed to atomically update metrics for product {}", productId, e);
            
            try {
                log.debug("Falling back to fetch+save approach for product {}", productId);
                updateProductMetricsFallback(productId, updates);
            } catch (Exception fallbackError) {
                log.error("Fallback update also failed for product {}", productId, fallbackError);
                throw fallbackError;
            }
        }
    }
    
    /**
     * Fallback method using fetch+save approach
     */
    private void updateProductMetricsFallback(String productId, Map<String, Object> updates) {
        Optional<ProductDocument> docOpt = searchRepository.findById(productId);
        if (docOpt.isPresent()) {
            ProductDocument doc = docOpt.get();
            
            if (updates.containsKey("clickCount")) {
                doc.setClickCount((doc.getClickCount() == null ? 0 : doc.getClickCount()) + 1);
            }
            
            if (updates.containsKey("purchaseCount")) {
                doc.setPurchaseCount((doc.getPurchaseCount() == null ? 0 : doc.getPurchaseCount()) + 1);
            }
            
            // Recalculate popularity
            long clicks = doc.getClickCount() == null ? 0 : doc.getClickCount();
            long purchases = doc.getPurchaseCount() == null ? 0 : doc.getPurchaseCount();
            doc.setPopularityScore(calculatePopularityScore(clicks, purchases));
            
            searchRepository.save(doc);
            log.debug("Fallback update completed for product {}", productId);
        } else {
            log.warn("Product {} not found for fallback update", productId);
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

            List<ProductDocument> documents = products.getContent().stream()
                .map(product -> {
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
     * Calculate popularity score based on clicks and purchases
     */
    private double calculatePopularityScore(long clickCount, long purchaseCount) {
        // Simple scoring: purchases are worth 10x clicks
        return (clickCount * 1.0) + (purchaseCount * 10.0);
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
     * Extract view increment from ProductViewed event message
     */
    private Long extractViewIncrement(Message message) {
        try {
            ProductEvent.ProductViewed event = objectMapper.readValue(message.getPayload(), ProductEvent.ProductViewed.class);
            
            if (event.getMetadata() != null && event.getMetadata().containsKey("view_increment")) {
                return Long.parseLong(event.getMetadata().get("view_increment"));
            }

            return 1L;
            
        } catch (Exception e) {
            log.warn("Failed to extract view increment from message, defaulting to 1: {}", e.getMessage());
            return 1L;
        }
    }
    
    /**
     * Extract total views from ProductViewed event message (for Redis batch sync)
     */
    private Long extractTotalViews(Message message) {
        try {
            ProductEvent.ProductViewed event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductViewed.class);
            
            if (event.getMetadata() != null && event.getMetadata().containsKey("total_views")) {
                return Long.parseLong(event.getMetadata().get("total_views"));
            }

            return null;
        } catch (Exception e) {
            log.debug("No total views in message (normal for real-time events): {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract popularity score from ProductViewed event message
     */
    private Double extractPopularityScore(Message message) {
        try {
            ProductEvent.ProductViewed event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductViewed.class);
            
            if (event.getMetadata() != null && event.getMetadata().containsKey("popularity_score")) {
                return Double.parseDouble(event.getMetadata().get("popularity_score"));
            }

            return null;

        } catch (Exception e) {
            log.debug("No popularity score in message: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract purchase increment from ProductPurchased event message
     */
    private Long extractPurchaseIncrement(Message message) {
        try {
            ProductEvent.ProductPurchased event = objectMapper.readValue(
                message.getPayload(), ProductEvent.ProductPurchased.class);
            
            if (event.getMetadata() != null && event.getMetadata().containsKey("purchase_increment")) {
                return Long.parseLong(event.getMetadata().get("purchase_increment"));
            }
            
            // Default to quantity or 1
            return event.getQuantity() != null ? event.getQuantity().longValue() : 1L;
            
        } catch (Exception e) {
            log.warn("Failed to extract purchase increment from message, defaulting to 1: {}", e.getMessage());
            return 1L;
        }
    }
    
    /**
     * Mark event as processed (in production, store in Redis or database)
     */
    private void markAsProcessed(String idempotencyKey) {
        // In production, store in cache or database with TTL
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
