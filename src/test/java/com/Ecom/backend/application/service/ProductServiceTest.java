package com.Ecom.backend.application.service;

import com.Ecom.backend.application.dto.CreateProductRequest;
import com.Ecom.backend.application.dto.ProductDto;
import com.Ecom.backend.application.dto.UpdateProductRequest;
import com.Ecom.backend.application.mapper.ProductMapper;
import com.Ecom.backend.application.service.OutboxService.OutboxEventService;
import com.Ecom.backend.application.service.ProductService.ProductService;
import com.Ecom.backend.domain.entity.Product;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductService.
 * Tests business logic, validation, and integration with outbox pattern.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private ProductMapper productMapper;
    
    @Mock
    private OutboxEventService outboxEventService;
    
    @InjectMocks
    private ProductService productService;
    
    private Product testProduct;
    private ProductDto testProductDto;
    private CreateProductRequest createRequest;
    private UpdateProductRequest updateRequest;
    
    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
            .id(1L)
            .name("Test Product")
            .description("Test Description")
            .categories(List.of("Electronics"))
            .price(new BigDecimal("99.99"))
            .sku("TEST-001")
            .clickCount(0L)
            .purchaseCount(0L)
            .popularityScore(0.0)
            .build();
        
        testProductDto = ProductDto.builder()
            .id(1L)
            .name("Test Product")
            .description("Test Description")
            .categories(List.of("Electronics"))
            .price(new BigDecimal("99.99"))
            .sku("TEST-001")
            .build();
        
        createRequest = CreateProductRequest.builder()
            .name("Test Product")
            .description("Test Description")
            .categories(List.of("Electronics"))
            .price(new BigDecimal("99.99"))
            .sku("TEST-001")
            .build();
        
        updateRequest = UpdateProductRequest.builder()
            .name("Updated Product")
            .description("Updated Description")
            .price(new BigDecimal("149.99"))
            .build();
    }
    
    @Test
    @DisplayName("Should create product successfully and publish event")
    void shouldCreateProductSuccessfully() {
        // Given
        when(productRepository.existsBySku("TEST-001")).thenReturn(false);
        when(productMapper.toEntity(createRequest)).thenReturn(testProduct);
        when(productRepository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toDto(testProduct)).thenReturn(testProductDto);
        
        // When
        ProductDto result = productService.createProduct(createRequest);
        
        // Then
        assertThat(result).isEqualTo(testProductDto);
        
        // Verify repository interactions
        verify(productRepository).existsBySku("TEST-001");
        verify(productRepository).save(testProduct);
        
        // Verify outbox event was published
        verify(outboxEventService).storeEvent(
            eq("1"),
            eq("Product"),
            eq("ProductCreated"),
            any()
        );
    }
    
    @Test
    @DisplayName("Should throw exception when creating product with existing SKU")
    void shouldThrowExceptionWhenCreatingProductWithExistingSku() {
        // Given
        when(productRepository.existsBySku("TEST-001")).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> productService.createProduct(createRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        
        // Verify no save or event publishing occurred
        verify(productRepository, never()).save(any());
        verify(outboxEventService, never()).storeEvent(any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("Should find product by ID")
    void shouldFindProductById() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productMapper.toDto(testProduct)).thenReturn(testProductDto);
        
        // When
        Optional<ProductDto> result = productService.getProductById(1L);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testProductDto);
    }
    
    @Test
    @DisplayName("Should return empty when product not found by ID")
    void shouldReturnEmptyWhenProductNotFoundById() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When
        Optional<ProductDto> result = productService.getProductById(1L);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should update product successfully and publish event")
    void shouldUpdateProductSuccessfully() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toDto(testProduct)).thenReturn(testProductDto);
        
        // When
        ProductDto result = productService.updateProduct(1L, updateRequest);
        
        // Then
        assertThat(result).isEqualTo(testProductDto);
        
        // Verify mapper was called to update entity
        verify(productMapper).updateEntity(testProduct, updateRequest);
        
        // Verify outbox event was published
        verify(outboxEventService).storeEvent(
            eq("1"),
            eq("Product"),
            eq("ProductUpdated"),
            any()
        );
    }
    
    @Test
    @DisplayName("Should throw exception when updating non-existent product")
    void shouldThrowExceptionWhenUpdatingNonExistentProduct() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> productService.updateProduct(1L, updateRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
        
        // Verify no save or event publishing occurred
        verify(productRepository, never()).save(any());
        verify(outboxEventService, never()).storeEvent(any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("Should delete product successfully and publish event")
    void shouldDeleteProductSuccessfully() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        
        // When
        productService.deleteProduct(1L);
        
        // Then
        verify(productRepository).delete(testProduct);
        
        // Verify outbox event was published
        verify(outboxEventService).storeEvent(
            eq("1"),
            eq("Product"),
            eq("ProductDeleted"),
            any()
        );
    }
    
    @Test
    @DisplayName("Should throw exception when deleting non-existent product")
    void shouldThrowExceptionWhenDeletingNonExistentProduct() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> productService.deleteProduct(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
        
        // Verify no deletion or event publishing occurred
        verify(productRepository, never()).delete(any());
        verify(outboxEventService, never()).storeEvent(any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("Should record product view and update metrics")
    void shouldRecordProductView() {
        // Given
        String userId = "user123";
        String sessionId = "session456";
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(testProduct)).thenReturn(testProduct);
        
        // When
        productService.recordProductView(1L, userId, sessionId);
        
        // Then
        // Verify click count was incremented (indirectly through save)
        verify(productRepository).save(testProduct);
        
        // Verify analytics event was published
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService).storeEvent(
            eq("1"),
            eq("Product"),
            eq("ProductViewed"),
            eventCaptor.capture()
        );
    }
    
    @Test
    @DisplayName("Should record product purchase and update metrics")
    void shouldRecordProductPurchase() {
        // Given
        String userId = "user123";
        String orderId = "order456";
        Integer quantity = 2;
        BigDecimal unitPrice = new BigDecimal("99.99");
        
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(testProduct)).thenReturn(testProduct);
        
        // When
        productService.recordProductPurchase(1L, userId, orderId, quantity, unitPrice);
        
        // Then
        // Verify purchase count was incremented (indirectly through save)
        verify(productRepository).save(testProduct);
        
        // Verify analytics event was published
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService).storeEvent(
            eq("1"),
            eq("Product"),
            eq("ProductPurchased"),
            eventCaptor.capture()
        );
    }
    
    @Test
    @DisplayName("Should handle SKU conflict during update")
    void shouldHandleSkuConflictDuringUpdate() {
        // Given
        UpdateProductRequest updateWithNewSku = UpdateProductRequest.builder()
            .sku("NEW-SKU")
            .build();
        
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.existsBySku("NEW-SKU")).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> productService.updateProduct(1L, updateWithNewSku))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        
        // Verify no save occurred
        verify(productRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("Should allow SKU update when SKU is unchanged")
    void shouldAllowSkuUpdateWhenSkuIsUnchanged() {
        // Given
        testProduct.setSku("EXISTING-SKU");
        UpdateProductRequest updateWithSameSku = UpdateProductRequest.builder()
            .sku("EXISTING-SKU")
            .name("Updated Name")
            .build();
        
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toDto(testProduct)).thenReturn(testProductDto);
        
        // When
        ProductDto result = productService.updateProduct(1L, updateWithSameSku);
        
        // Then
        assertThat(result).isEqualTo(testProductDto);
        verify(productRepository, never()).existsBySku(any()); // SKU check should be skipped
        verify(productRepository).save(testProduct);
    }
}
