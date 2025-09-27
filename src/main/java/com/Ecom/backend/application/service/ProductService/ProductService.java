package com.Ecom.backend.application.service.ProductService;

import com.Ecom.backend.application.dto.CreateProductRequest;
import com.Ecom.backend.application.dto.ProductDto;
import com.Ecom.backend.application.dto.UpdateProductRequest;
import com.Ecom.backend.application.dto.event.ProductEvent;
import com.Ecom.backend.application.mapper.ProductMapper;
import com.Ecom.backend.application.service.OutboxService.OutboxEventService;
import com.Ecom.backend.application.service.ViewService.ViewCounterService;
import com.Ecom.backend.domain.entity.Product;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.OptimisticLockException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Product service implementing core business logic.
 * 
 * This service follows the Single Responsibility Principle by focusing on
 * product-related business operations. It coordinates between the domain layer,
 * repository layer, and event publishing through the outbox pattern.
 * 
 * All public methods that modify data are transactional to ensure consistency
 * with the outbox pattern.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final OutboxEventService outboxEventService;
    private final ViewCounterService viewCounterService;
    
    /**
     * Create a new product
     * 
     * @param request Product creation request
     * @return Created product DTO
     * @throws IllegalArgumentException if SKU already exists
     */
    public ProductDto createProduct(CreateProductRequest request) {
        log.debug("Creating product with SKU: {}", request.getSku());
        
        // Validate SKU uniqueness
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("Product with SKU " + request.getSku() + " already exists");
        }
        
        // Convert request to entity and save
        Product product = productMapper.toEntity(request);
        product.calculatePopularityScore(); // Initialize score
        Product savedProduct = productRepository.save(product);
        
        // Publish event through outbox pattern
        ProductEvent.ProductCreated event = ProductEvent.ProductCreated.builder()
            .productId(savedProduct.getId())
            .name(savedProduct.getName())
            .description(savedProduct.getDescription())
            .categories(savedProduct.getCategories())
            .price(savedProduct.getPrice())
            .sku(savedProduct.getSku())
            .attributes(savedProduct.getAttributes())
            .images(savedProduct.getImages())
            .createdAt(savedProduct.getCreatedAt())
            .createdBy("system") // In real app, this would come from security context
            .build();
        
        outboxEventService.storeEvent(
            savedProduct.getId().toString(),
            "Product",
            "ProductCreated",
            event
        );
        
        log.info("Created product {} with ID {}", savedProduct.getSku(), savedProduct.getId());
        return productMapper.toDto(savedProduct);
    }
    
    /**
     * Get product by SKU
     * 
     * @param sku Product SKU
     * @return Product DTO if found
     */
    public Optional<ProductDto> getProductBySku(String sku) {
        log.debug("Fetching product with SKU: {}", sku);
        return productRepository.findBySku(sku)
            .map(productMapper::toDto);
    }
    
    /**
     * Update an existing product
     * 
     * @param productId Product ID to update
     * @param request Update request
     * @return Updated product DTO
     * @throws IllegalArgumentException if product not found or SKU conflict
     */
    @Transactional
    public ProductDto updateProduct(Long productId, UpdateProductRequest request) {
        log.debug("Updating product with ID: {}", productId);
        
        try {
            Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
            
            // Check SKU uniqueness if SKU is being updated
            if (request.getSku() != null && !request.getSku().equals(existingProduct.getSku())) {
                if (productRepository.existsBySku(request.getSku())) {
                    throw new IllegalArgumentException("Product with SKU " + request.getSku() + " already exists");
                }
            }
            
            // Store previous values for event
            String previousName = existingProduct.getName();
            BigDecimal previousPrice = existingProduct.getPrice();
            
            // Update entity
            productMapper.updateEntity(existingProduct, request);
            Product updatedProduct = productRepository.save(existingProduct);
            
            // Publish event through outbox pattern
            ProductEvent.ProductUpdated event = ProductEvent.ProductUpdated.builder()
                .productId(updatedProduct.getId())
                .name(updatedProduct.getName())
                .description(updatedProduct.getDescription())
                .categories(updatedProduct.getCategories())
                .price(updatedProduct.getPrice())
                .sku(updatedProduct.getSku())
                .attributes(updatedProduct.getAttributes())
                .images(updatedProduct.getImages())
                .updatedAt(updatedProduct.getUpdatedAt())
                .updatedBy("system") // In real app, this would come from security context
                .build();
            
            outboxEventService.storeEvent(
                updatedProduct.getId().toString(),
                "Product",
                "ProductUpdated",
                event
            );
        
            log.info("Updated product {} with ID {}", updatedProduct.getSku(), updatedProduct.getId());
            return productMapper.toDto(updatedProduct);
            
        } catch (OptimisticLockException e) {
            log.warn("Concurrent update detected for product ID: {}. Another transaction modified this product.", productId);
            throw new IllegalArgumentException("Product was modified by another process. Please refresh and try again.");
        }
    }
    
    /**
     * Delete a product
     * 
     * @param productId Product ID to delete
     * @throws IllegalArgumentException if product not found
     */
    @Transactional
    public void deleteProduct(Long productId) {
        log.debug("Deleting product with ID: {}", productId);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
        
        // Publish event before deletion
        ProductEvent.ProductDeleted event = ProductEvent.ProductDeleted.builder()
            .productId(product.getId())
            .sku(product.getSku())
            .name(product.getName())
            .deletedAt(java.time.LocalDateTime.now())
            .deletedBy("system") // In real app, this would come from security context
            .reason("User requested deletion")
            .build();
        
        outboxEventService.storeEvent(
            product.getId().toString(),
            "Product",
            "ProductDeleted",
            event
        );
        
        productRepository.delete(product);
        
        log.info("Deleted product {} with ID {}", product.getSku(), product.getId());
    }
    /**
     * Get all products with pagination
     *
     * @param pageable Pagination parameters
     * @return Page of product DTOs
     */
    @Transactional(readOnly = true)
    public Page<ProductDto> getAllProducts(Pageable pageable) {
        log.debug("Fetching all products with pagination: {}", pageable);
        Page<Product> products = productRepository.findAllWithCategories(pageable);
        return products.map(productMapper::toDto);
    }
    /**
     * Record a product purchase for analytics
     * 
     * @param productId Product ID that was purchased
     * @param userId User ID
     * @param orderId Order ID
     * @param quantity Quantity purchased
     * @param unitPrice Unit price at time of purchase
     */
    @Transactional
    public void recordProductPurchase(Long productId, String userId, String orderId, 
                                      Integer quantity, BigDecimal unitPrice) {
        log.debug("Recording purchase for product ID: {} by user: {}", productId, userId);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
        
        // Update purchase count in domain entity
        for (int i = 0; i < quantity; i++) {
            product.incrementPurchaseCount();
        }
        productRepository.save(product);
        
        // Publish analytics event
        ProductEvent.ProductPurchased event = ProductEvent.ProductPurchased.builder()
            .productId(productId)
            .sku(product.getSku())
            .userId(userId)
            .orderId(orderId)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
            .purchasedAt(java.time.LocalDateTime.now())
            .build();
        
        outboxEventService.storeEvent(
            productId.toString(),
            "Product",
            "ProductPurchased",
            event
        );
    }

    /**
     * Determine if we should publish a real-time view event
     * This is optional and can be used for immediate analytics processing
     */
    private boolean shouldPublishRealTimeEvent(String userId, String sessionId) {
        // Only publish real-time events for authenticated users or important sessions
        // This reduces event volume while keeping the most valuable analytics
        return userId != null && !userId.equals("anonymous");
    }

    /**
     * Publish real-time view event for immediate analytics processing
     */
    private void publishRealTimeViewEvent(Long productId, String userId, String sessionId) {
        try {
            ProductEvent.ProductViewed event = ProductEvent.ProductViewed.builder()
                    .productId(productId)
                    .sku("") // SKU will be populated by event processor if needed
                    .userId(userId)
                    .sessionId(sessionId)
                    .viewedAt(java.time.LocalDateTime.now())
                    .referrer("real-time")
                    .metadata(java.util.Map.of(
                            "event_source", "real-time",
                            "view_increment", "1"
                    ))
                    .build();

            outboxEventService.storeEvent(
                    productId.toString(),
                    "Product",
                    "ProductViewed",
                    event
            );

            log.debug("Published real-time view event for product {}", productId);

        } catch (Exception e) {
            log.warn("Failed to publish real-time view event for product {}: {}", productId, e.getMessage());
            // Don't throw - this is optional analytics
        }
    }


    /**
     * Get products by category with pagination
     * 
     * @param categories Categories to filter by
     * @param pageable Pagination parameters
     * @return Page of products in the specified categories
     */
    @Transactional(readOnly = true)
    public Page<ProductDto> getProductsByCategories(List<String> categories, Pageable pageable) {
        Page<Product> products = productRepository.findByCategoriesIn(categories, pageable);
        return products.map(productMapper::toDto);
    }

    /**
     * Get top products by popularity
     * 
     * @param limit Maximum number of products to return
     * @return List of top products by popularity score
     */
    @Transactional(readOnly = true)
    public List<ProductDto> getTopProductsByPopularity(int limit) {
        List<Product> topProducts = productRepository.findTopByPopularity(
            org.springframework.data.domain.PageRequest.of(0, limit));
        return productMapper.toDtoList(topProducts);
    }
    /**
     * Search products with filters and pagination
     *
     * @param query Search query (optional)
     * @param categories Category filter (optional)
     * @param minPrice Minimum price filter (optional)
     * @param maxPrice Maximum price filter (optional)
     * @param pageable Pagination parameters
     * @return Page of matching products
     */
    @Transactional(readOnly = true)
    public Page<ProductDto> searchProducts(
            String query,
            List<String> categories,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        log.debug("Searching products with query: {}, categories: {}, price range: {} - {}",
                query, categories, minPrice, maxPrice);

        Page<Product> products = productRepository.searchWithFilters(
                query, categories, minPrice, maxPrice, pageable);

        return products.map(productMapper::toDto);
    }

    /**
     * Find related products for a given product
     *
     * @param productId Product ID to find related products for
     * @param limit Maximum number of related products to return
     * @return List of related product DTOs
     */
    @Transactional(readOnly = true)
    public List<ProductDto> findRelatedProducts(Long productId, int limit) {
        log.debug("Finding related products for product ID: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        List<Product> relatedProducts = productRepository.findRelatedProductsByCategory(
                productId, product.getCategories(),
                org.springframework.data.domain.PageRequest.of(0, limit));

        return productMapper.toDtoList(relatedProducts);
    }
}
