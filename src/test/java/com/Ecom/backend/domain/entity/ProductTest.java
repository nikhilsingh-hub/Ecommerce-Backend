package com.Ecom.backend.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Product domain entity.
 * Tests business logic and domain rules.
 */
@DisplayName("Product Entity Tests")
class ProductTest {
    
    private Product product;
    
    @BeforeEach
    void setUp() {
        product = Product.builder()
            .id(1L)
            .name("Test Product")
            .description("Test Description")
            .categories(List.of("Electronics", "Gadgets"))
            .price(new BigDecimal("99.99"))
            .sku("TEST-001")
            .attributes(Map.of("color", "blue", "size", "medium"))
            .images(List.of("image1.jpg", "image2.jpg"))
            .clickCount(10L)
            .purchaseCount(5L)
            .popularityScore(0.0)
            .build();
    }
    
    @Test
    @DisplayName("Should calculate popularity score correctly")
    void shouldCalculatePopularityScore() {
        // When
        product.calculatePopularityScore();
        
        // Then
        double expectedScore = (10 * 0.3) + (5 * 0.7); // clicks * 0.3 + purchases * 0.7
        assertThat(product.getPopularityScore()).isEqualTo(expectedScore);
    }
    
    @Test
    @DisplayName("Should increment click count and recalculate popularity score")
    void shouldIncrementClickCount() {
        // Given
        Long initialClickCount = product.getClickCount();
        Double initialScore = product.getPopularityScore();
        
        // When
        product.incrementClickCount();
        
        // Then
        assertThat(product.getClickCount()).isEqualTo(initialClickCount + 1);
        assertThat(product.getPopularityScore()).isGreaterThan(initialScore);
    }
    
    @Test
    @DisplayName("Should increment purchase count and recalculate popularity score")
    void shouldIncrementPurchaseCount() {
        // Given
        Long initialPurchaseCount = product.getPurchaseCount();
        Double initialScore = product.getPopularityScore();
        
        // When
        product.incrementPurchaseCount();
        
        // Then
        assertThat(product.getPurchaseCount()).isEqualTo(initialPurchaseCount + 1);
        assertThat(product.getPopularityScore()).isGreaterThan(initialScore);
    }
    
    @Test
    @DisplayName("Should return true for available product with valid price")
    void shouldReturnTrueForAvailableProduct() {
        // Given
        product.setPrice(new BigDecimal("50.00"));
        
        // When & Then
        assertThat(product.isAvailable()).isTrue();
    }
    
    @Test
    @DisplayName("Should return false for product with zero price")
    void shouldReturnFalseForZeroPriceProduct() {
        // Given
        product.setPrice(BigDecimal.ZERO);
        
        // When & Then
        assertThat(product.isAvailable()).isFalse();
    }
    
    @Test
    @DisplayName("Should return false for product with null price")
    void shouldReturnFalseForNullPriceProduct() {
        // Given
        product.setPrice(null);
        
        // When & Then
        assertThat(product.isAvailable()).isFalse();
    }
    
    @Test
    @DisplayName("Should return false for product with negative price")
    void shouldReturnFalseForNegativePriceProduct() {
        // Given
        product.setPrice(new BigDecimal("-10.00"));
        
        // When & Then
        assertThat(product.isAvailable()).isFalse();
    }
    
    @Test
    @DisplayName("Should properly build product with builder pattern")
    void shouldBuildProductWithBuilder() {
        // When
        Product builtProduct = Product.builder()
            .name("Builder Test")
            .description("Builder Description")
            .price(new BigDecimal("199.99"))
            .sku("BUILDER-001")
            .build();
        
        // Then
        assertThat(builtProduct.getName()).isEqualTo("Builder Test");
        assertThat(builtProduct.getDescription()).isEqualTo("Builder Description");
        assertThat(builtProduct.getPrice()).isEqualTo(new BigDecimal("199.99"));
        assertThat(builtProduct.getSku()).isEqualTo("BUILDER-001");
        assertThat(builtProduct.getClickCount()).isEqualTo(0L);
        assertThat(builtProduct.getPurchaseCount()).isEqualTo(0L);
        assertThat(builtProduct.getPopularityScore()).isEqualTo(0.0);
    }
}
