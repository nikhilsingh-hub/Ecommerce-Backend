package com.Ecom.backend.infrastructure.elasticsearch;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import com.Ecom.backend.domain.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch document representing a product for search operations.
 * This document is optimized for full-text search, faceting, and aggregations.
 * 
 * The mapping includes:
 * - Full-text search fields for name and description
 * - Keyword fields for exact matching and faceting
 * - Nested objects for complex attributes
 * - Proper field mappings for performance
 */
@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-settings.json")
@Mapping(mappingPath = "elasticsearch/product-mapping.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {
    
    @Id
    private String id; // Using String to match Elasticsearch ID format
    
    @Field(type = FieldType.Long)
    private Long productId; // Original database ID
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "standard"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword),
            @InnerField(suffix = "suggest", type = FieldType.Search_As_You_Type)
        }
    )
    private String name;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;
    
    @Field(type = FieldType.Keyword)
    private List<String> categories;
    
    @Field(type = FieldType.Double)
    private BigDecimal price;
    
    @Field(type = FieldType.Keyword)
    private String sku;
    
    @Field(type = FieldType.Object)
    private Map<String, String> attributes;
    
    @Field(type = FieldType.Keyword)
    private List<String> images;
    
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Field(type = FieldType.Long)
    private Long clickCount;
    
    @Field(type = FieldType.Long)
    private Long purchaseCount;
    
    @Field(type = FieldType.Double)
    private Double popularityScore;
    
    // Additional fields for search optimization
    @Field(type = FieldType.Text, analyzer = "product_search")
    private String allText; // Combined searchable text
    
    @Field(type = FieldType.Keyword)
    private List<String> tags; // Generated tags for better categorization
    
    @Field(type = FieldType.Boolean)
    private Boolean inStock; // Derived field for availability
    
    @Field(type = FieldType.Keyword)
    private String priceRange; // Categorized price range (e.g., "0-50", "50-100")
    
    @Field(type = FieldType.Double)
    private Double scoreBoost; // Field for custom scoring
    
    /**
     * Create a ProductDocument from domain Product entity
     */
    public static ProductDocument fromProduct(Product product) {
        return ProductDocument.builder()
            .id(product.getId().toString())
            .productId(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .categories(product.getCategories())
            .price(product.getPrice())
            .sku(product.getSku())
            .attributes(product.getAttributes())
            .images(product.getImages())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .clickCount(product.getClickCount())
            .purchaseCount(product.getPurchaseCount())
            .popularityScore(product.getPopularityScore())
            .allText(buildAllText(product))
            .tags(generateTags(product))
            .inStock(product.isAvailable())
            .priceRange(calculatePriceRange(product.getPrice()))
            .scoreBoost(calculateScoreBoost(product))
            .build();
    }
    
    /**
     * Build combined searchable text from all relevant fields
     */
    private static String buildAllText(com.Ecom.backend.domain.entity.Product product) {
        StringBuilder allText = new StringBuilder();
        
        if (product.getName() != null) {
            allText.append(product.getName()).append(" ");
        }
        
        if (product.getDescription() != null) {
            allText.append(product.getDescription()).append(" ");
        }
        
        if (product.getSku() != null) {
            allText.append(product.getSku()).append(" ");
        }
        
        if (product.getCategories() != null) {
            allText.append(String.join(" ", product.getCategories())).append(" ");
        }
        
        if (product.getAttributes() != null) {
            product.getAttributes().values().forEach(value -> 
                allText.append(value).append(" "));
        }
        
        return allText.toString().trim();
    }
    
    /**
     * Generate tags based on product properties for better categorization
     */
    private static List<String> generateTags(Product product) {
        List<String> tags = new java.util.ArrayList<>();
        
        // Add category-based tags
        if (product.getCategories() != null) {
            tags.addAll(product.getCategories());
        }
        
        // Add price-based tags
        if (product.getPrice() != null) {
            if (product.getPrice().compareTo(new BigDecimal("100")) < 0) {
                tags.add("budget");
            } else if (product.getPrice().compareTo(new BigDecimal("500")) > 0) {
                tags.add("premium");
            } else {
                tags.add("mid-range");
            }
        }
        
        // Add popularity-based tags
        if (product.getPopularityScore() != null) {
            if (product.getPopularityScore() > 50) {
                tags.add("popular");
            }
            if (product.getPurchaseCount() > 100) {
                tags.add("bestseller");
            }
        }
        
        // Add availability tags
        if (product.isAvailable()) {
            tags.add("available");
        }
        
        return tags;
    }
    
    /**
     * Calculate price range category for faceting
     */
    private static String calculatePriceRange(BigDecimal price) {
        if (price == null) {
            return "unknown";
        }
        
        if (price.compareTo(new BigDecimal("25")) <= 0) {
            return "0-25";
        } else if (price.compareTo(new BigDecimal("50")) <= 0) {
            return "25-50";
        } else if (price.compareTo(new BigDecimal("100")) <= 0) {
            return "50-100";
        } else if (price.compareTo(new BigDecimal("250")) <= 0) {
            return "100-250";
        } else if (price.compareTo(new BigDecimal("500")) <= 0) {
            return "250-500";
        } else {
            return "500+";
        }
    }
    
    /**
     * Calculate score boost for relevance tuning
     */
    private static Double calculateScoreBoost(Product product) {
        double boost = 1.0;
        
        // Boost based on popularity
        if (product.getPopularityScore() != null) {
            boost += (product.getPopularityScore() / 100.0) * 0.5;
        }
        
        // Boost recent products
        if (product.getCreatedAt() != null) {
            long daysSinceCreation = java.time.temporal.ChronoUnit.DAYS.between(
                product.getCreatedAt(), LocalDateTime.now());
            if (daysSinceCreation < 30) {
                boost += 0.2;
            }
        }
        
        // Boost products with images
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            boost += 0.1;
        }
        
        return Math.min(boost, 2.0); // Cap boost at 2.0
    }
}
