package com.Ecom.backend.integration;

import com.Ecom.backend.application.dto.CreateProductRequest;
import com.Ecom.backend.application.dto.ProductDto;
import com.Ecom.backend.application.dto.UpdateProductRequest;
import com.Ecom.backend.domain.entity.OutboxEvent;
import com.Ecom.backend.domain.entity.Product;
import com.Ecom.backend.infrastructure.repository.OutboxEventRepository;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete product flow.
 * Tests the entire stack from REST API to database and event publishing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Product Integration Tests")
class ProductIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Clean up database
        outboxEventRepository.deleteAll();
        productRepository.deleteAll();
    }
    
    @Test
    @DisplayName("Should create product and publish outbox event")
    @Transactional
    void shouldCreateProductAndPublishOutboxEvent() throws Exception {
        // Given
        CreateProductRequest request = CreateProductRequest.builder()
            .name("Integration Test Product")
            .description("Test product for integration testing")
            .categories(List.of("Electronics", "Test"))
            .price(new BigDecimal("199.99"))
            .sku("INT-TEST-001")
            .build();
        
        // When
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Integration Test Product"))
            .andExpect(jsonPath("$.data.sku").value("INT-TEST-001"))
            .andReturn();
        
        // Then
        // Verify product was saved to database
        Optional<Product> savedProduct = productRepository.findBySku("INT-TEST-001");
        assertThat(savedProduct).isPresent();
        assertThat(savedProduct.get().getName()).isEqualTo("Integration Test Product");
        
        // Verify outbox event was created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdAndAggregateTypeOrderByCreatedAtAsc(
            savedProduct.get().getId().toString(), "Product");
        assertThat(outboxEvents).hasSize(1);
        
        OutboxEvent event = outboxEvents.get(0);
        assertThat(event.getEventType()).isEqualTo("ProductCreated");
        assertThat(event.getProcessed()).isFalse();
    }
    
    @Test
    @DisplayName("Should return 409 when creating product with duplicate SKU")
    void shouldReturn409WhenCreatingProductWithDuplicateSku() throws Exception {
        // Given - create initial product
        Product existingProduct = Product.builder()
            .name("Existing Product")
            .description("Already exists")
            .categories(List.of("Test"))
            .price(new BigDecimal("99.99"))
            .sku("DUPLICATE-SKU")
            .build();
        productRepository.save(existingProduct);
        
        CreateProductRequest request = CreateProductRequest.builder()
            .name("New Product")
            .description("Should fail")
            .categories(List.of("Test"))
            .price(new BigDecimal("149.99"))
            .sku("DUPLICATE-SKU")
            .build();
        
        // When & Then
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("PRODUCT_ALREADY_EXISTS"));
    }
    
    @Test
    @DisplayName("Should update product and publish outbox event")
    @Transactional
    void shouldUpdateProductAndPublishOutboxEvent() throws Exception {
        // Given - create initial product
        Product product = Product.builder()
            .name("Original Product")
            .description("Original description")
            .categories(List.of("Electronics"))
            .price(new BigDecimal("99.99"))
            .sku("UPDATE-TEST-001")
            .build();
        Product savedProduct = productRepository.save(product);
        
        UpdateProductRequest updateRequest = UpdateProductRequest.builder()
            .name("Updated Product")
            .description("Updated description")
            .price(new BigDecimal("149.99"))
            .build();
        
        // When
        mockMvc.perform(put("/api/v1/products/" + savedProduct.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Updated Product"))
            .andExpect(jsonPath("$.data.price").value(149.99));
        
        // Then
        // Verify product was updated in database
        Optional<Product> updatedProduct = productRepository.findById(savedProduct.getId());
        assertThat(updatedProduct).isPresent();
        assertThat(updatedProduct.get().getName()).isEqualTo("Updated Product");
        assertThat(updatedProduct.get().getPrice()).isEqualTo(new BigDecimal("149.99"));
        
        // Verify outbox event was created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdAndAggregateTypeOrderByCreatedAtAsc(
            savedProduct.getId().toString(), "Product");
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ProductUpdated");
    }
    
    @Test
    @DisplayName("Should delete product and publish outbox event")
    @Transactional
    void shouldDeleteProductAndPublishOutboxEvent() throws Exception {
        // Given - create initial product
        Product product = Product.builder()
            .name("Product To Delete")
            .description("Will be deleted")
            .categories(List.of("Test"))
            .price(new BigDecimal("99.99"))
            .sku("DELETE-TEST-001")
            .build();
        Product savedProduct = productRepository.save(product);
        Long productId = savedProduct.getId();
        
        // When
        mockMvc.perform(delete("/api/v1/products/" + productId))
            .andExpect(status().isNoContent());
        
        // Then
        // Verify product was deleted from database
        Optional<Product> deletedProduct = productRepository.findById(productId);
        assertThat(deletedProduct).isEmpty();
        
        // Verify outbox event was created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateIdAndAggregateTypeOrderByCreatedAtAsc(
            productId.toString(), "Product");
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ProductDeleted");
    }
    
    @Test
    @DisplayName("Should record product view and create analytics event")
    @Transactional
    void shouldRecordProductViewAndCreateAnalyticsEvent() throws Exception {
        // Given - create initial product
        Product product = Product.builder()
            .name("Viewable Product")
            .description("Can be viewed")
            .categories(List.of("Test"))
            .price(new BigDecimal("99.99"))
            .sku("VIEW-TEST-001")
            .clickCount(5L)
            .build();
        Product savedProduct = productRepository.save(product);
        
        // When
        mockMvc.perform(get("/api/v1/products/" + savedProduct.getId())
                .param("userId", "user123")
                .param("sessionId", "session456"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(savedProduct.getId()));
        
        // Then
        // Verify click count was incremented
        Optional<Product> viewedProduct = productRepository.findById(savedProduct.getId());
        assertThat(viewedProduct).isPresent();
        assertThat(viewedProduct.get().getClickCount()).isEqualTo(6L);
        
        // Verify analytics event was created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByEventTypeOrderByCreatedAtAsc("ProductViewed");
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo(savedProduct.getId().toString());
    }
    
    @Test
    @DisplayName("Should validate input and return 400 for invalid data")
    void shouldValidateInputAndReturn400ForInvalidData() throws Exception {
        // Given - invalid request with missing required fields
        CreateProductRequest invalidRequest = CreateProductRequest.builder()
            .name("") // Invalid - empty name
            .price(new BigDecimal("-10.00")) // Invalid - negative price
            .sku("") // Invalid - empty SKU
            .build();
        
        // When & Then
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fieldErrors").exists());
    }
    
    @Test
    @DisplayName("Should search products with filters")
    void shouldSearchProductsWithFilters() throws Exception {
        // Given - create test products
        Product product1 = Product.builder()
            .name("Wireless Headphones")
            .description("High quality audio")
            .categories(List.of("Electronics", "Audio"))
            .price(new BigDecimal("99.99"))
            .sku("SEARCH-001")
            .build();
        
        Product product2 = Product.builder()
            .name("Bluetooth Speaker")
            .description("Portable sound")
            .categories(List.of("Electronics", "Audio"))
            .price(new BigDecimal("149.99"))
            .sku("SEARCH-002")
            .build();
        
        productRepository.saveAll(List.of(product1, product2));
        
        // When & Then
        mockMvc.perform(get("/api/v1/products/search")
                .param("q", "audio")
                .param("categories", "Electronics")
                .param("minPrice", "50")
                .param("maxPrice", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.products").isArray())
            .andExpect(jsonPath("$.data.totalElements").exists());
    }
    
    @Test
    @DisplayName("Should handle concurrent product creation")
    @Transactional
    void shouldHandleConcurrentProductCreation() throws Exception {
        // This test verifies that the application can handle concurrent requests
        // In a real scenario, this would test database transaction isolation
        
        CreateProductRequest request1 = CreateProductRequest.builder()
            .name("Concurrent Product 1")
            .description("First concurrent product")
            .categories(List.of("Test"))
            .price(new BigDecimal("99.99"))
            .sku("CONCURRENT-001")
            .build();
        
        CreateProductRequest request2 = CreateProductRequest.builder()
            .name("Concurrent Product 2")
            .description("Second concurrent product")
            .categories(List.of("Test"))
            .price(new BigDecimal("149.99"))
            .sku("CONCURRENT-002")
            .build();
        
        // When - create products concurrently (simulate with sequential calls in test)
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());
        
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
        
        // Then
        assertThat(productRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }
}
