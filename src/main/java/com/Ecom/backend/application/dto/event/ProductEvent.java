package com.Ecom.backend.application.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Base class for product-related events.
 * These events are stored in the outbox and published to the message broker.
 */
public abstract class ProductEvent {
    
    /**
     * Event published when a product is created
     */
    @Data
    @Builder
    @lombok.EqualsAndHashCode(callSuper=false)
    public static class ProductCreated extends ProductEvent {
        private Long productId;
        private String name;
        private String description;
        private List<String> categories;
        private BigDecimal price;
        private String sku;
        private Map<String, String> attributes;
        private List<String> images;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        
        private String createdBy;
    }
    
    /**
     * Event published when a product is updated
     */
    @Data
    @Builder
    @lombok.EqualsAndHashCode(callSuper=false)
    public static class ProductUpdated extends ProductEvent {
        private Long productId;
        private String name;
        private String description;
        private List<String> categories;
        private BigDecimal price;
        private String sku;
        private Map<String, String> attributes;
        private List<String> images;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime updatedAt;
        
        private String updatedBy;
        
        // Fields that were changed (for partial updates)
        private List<String> changedFields;
        private Map<String, Object> previousValues;
    }
    
    /**
     * Event published when a product is deleted
     */
    @Data
    @Builder
    @lombok.EqualsAndHashCode(callSuper=false)
    public static class ProductDeleted extends ProductEvent {
        private Long productId;
        private String sku;
        private String name;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime deletedAt;
        
        private String deletedBy;
        private String reason;
    }
    
    /**
     * Event published when a product is viewed (for analytics)
     */
    @Data
    @Builder
    @lombok.EqualsAndHashCode(callSuper=false)
    public static class ProductViewed extends ProductEvent {
        private Long productId;
        private String sku;
        private String userId;
        private String sessionId;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime viewedAt;
        
        private String referrer;
        private Map<String, String> metadata;
    }
    
    /**
     * Event published when a product is purchased
     */
    @Data
    @Builder
    @lombok.EqualsAndHashCode(callSuper=false)
    public static class ProductPurchased extends ProductEvent {
        private Long productId;
        private String sku;
        private String userId;
        private String orderId;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime purchasedAt;
        
        private Map<String, String> metadata;
    }
    
    /**
     * Event published when product inventory changes
     */
    @Data
    @Builder
    @lombok.EqualsAndHashCode(callSuper=false)
    public static class ProductInventoryChanged extends ProductEvent {
        private Long productId;
        private String sku;
        private Integer previousQuantity;
        private Integer newQuantity;
        private Integer quantityChange;
        private String changeReason;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime changedAt;
        
        private String changedBy;
    }
}
