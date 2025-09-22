package com.Ecom.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product domain entity representing the core business object.
 * This entity follows DDD principles and contains business logic.
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_price", columnList = "price"),
    @Index(name = "idx_product_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "product_categories", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "category")
    private List<String> categories;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(unique = true, nullable = false, length = 100)
    private String sku;

    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attribute_key")
    @Column(name = "attribute_value", columnDefinition = "TEXT")
    private Map<String, String> attributes;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    private List<String> images;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Popularity metrics for related products algorithm
    @Column(nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long purchaseCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Double popularityScore = 0.0;

    /**
     * Business logic: Calculate popularity score based on clicks and purchases
     */
    public void calculatePopularityScore() {
        // Simple algorithm: weighted sum of clicks and purchases
        this.popularityScore = (clickCount * 0.3) + (purchaseCount * 0.7);
    }

    /**
     * Business logic: Increment click count
     */
    public void incrementClickCount() {
        this.clickCount++;
        calculatePopularityScore();
    }

    /**
     * Business logic: Increment purchase count
     */
    public void incrementPurchaseCount() {
        this.purchaseCount++;
        calculatePopularityScore();
    }

    /**
     * Business logic: Check if product is available for purchase
     */
    public boolean isAvailable() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
