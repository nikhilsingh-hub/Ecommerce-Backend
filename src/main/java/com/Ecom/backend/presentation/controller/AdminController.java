package com.Ecom.backend.presentation.controller;

import com.Ecom.backend.application.dto.event.ProductEvent;
import com.Ecom.backend.application.service.ProductService.ProductElasticsearchSyncService;
import com.Ecom.backend.application.service.OutboxService.OutboxEventService;
import com.Ecom.backend.application.service.ViewService.ViewCounterService;
import com.Ecom.backend.application.service.ViewService.ViewSyncService;
import com.Ecom.backend.domain.entity.OutboxEvent;
import com.Ecom.backend.infrastructure.pubsub.Interface.MessagePublisher;
import com.Ecom.backend.presentation.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for management and testing operations.
 * Provides endpoints for batch operations, system maintenance, and testing.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative operations")
@Slf4j
@RequiredArgsConstructor
public class AdminController {
    
    private final MessagePublisher messagePublisher;
    private final ProductElasticsearchSyncService syncService;
    private final OutboxEventService outboxEventService;
    private final ViewCounterService viewCounterService;
    private final ViewSyncService viewSyncService;
    
    @Operation(
        summary = "Store batch product events in outbox",
        description = "Store a batch of product events in the outbox table for reliable processing via the Transactional Outbox Pattern"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product batch stored in outbox successfully"
        )
    })
    @PostMapping("/publish-batch")
    @Transactional
    public ResponseEntity<ApiResponse<BatchPublishResponse>> publishBatch(
            @Parameter(description = "Number of product events to create")
            @RequestParam(defaultValue = "10") Integer messageCount,
            @Parameter(description = "Event type for the messages")
            @RequestParam(defaultValue = "ProductCreated") String eventType,
            HttpServletRequest httpRequest) {
        
        log.info("Storing batch of {} product events in outbox", messageCount);
        
        try {
            List<ProductEvent.ProductCreated> eventDataList = generateProductEventData(eventType, messageCount);
            List<OutboxEvent> storedEvents = new java.util.ArrayList<>();
            
            // Store each event in the outbox table
            for (int i = 0; i < eventDataList.size(); i++) {
                ProductEvent.ProductCreated eventData = eventDataList.get(i);
                String aggregateId = "product-" + eventData.getProductId();
                
                OutboxEvent storedEvent = outboxEventService.storeEvent(
                    aggregateId,
                    "Product", 
                    eventType,
                    eventData
                );
                storedEvents.add(storedEvent);
            }
            
            BatchPublishResponse response = BatchPublishResponse.builder()
                .topic("product-events")
                .messageCount(storedEvents.size())
                .eventType(eventType)
                .firstEventId(storedEvents.isEmpty() ? null : storedEvents.get(0).getId())
                .lastEventId(storedEvents.isEmpty() ? null : 
                    storedEvents.get(storedEvents.size() - 1).getId())
                .storedInOutbox(true)
                .build();
            
            ApiResponse<BatchPublishResponse> apiResponse = ApiResponse.<BatchPublishResponse>builder()
                .success(true)
                .message("Product batch stored in outbox successfully - events will be processed asynchronously")
                .data(response)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.ok(apiResponse);
            
        } catch (Exception e) {
            log.error("Failed to store batch in outbox", e);
            throw new RuntimeException("Failed to store product batch in outbox: " + e.getMessage());
        }
    }
    
    @Operation(
        summary = "Trigger full Elasticsearch sync",
        description = "Manually trigger a full synchronization from MySQL to Elasticsearch"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "Sync started successfully"
        )
    })
    @PostMapping("/sync/full")
    public ResponseEntity<ApiResponse<SyncResponse>> triggerFullSync(
            HttpServletRequest httpRequest) {
        
        log.info("Triggering full Elasticsearch sync");
        
        try {
            syncService.performFullSync();
            
            SyncResponse response = SyncResponse.builder()
                .syncType("full")
                .status("started")
                .message("Full synchronization has been initiated")
                .build();
            
            ApiResponse<SyncResponse> apiResponse = ApiResponse.<SyncResponse>builder()
                .success(true)
                .message("Full sync started")
                .data(response)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.accepted().body(apiResponse);
            
        } catch (Exception e) {
            log.error("Failed to trigger full sync", e);
            throw new RuntimeException("Failed to trigger full sync: " + e.getMessage());
        }
    }
    
    @Operation(
        summary = "Get synchronization status",
        description = "Get the current synchronization status between MySQL and Elasticsearch"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Sync status retrieved successfully"
        )
    })
    @GetMapping("/sync/status")
    public ResponseEntity<ApiResponse<ProductElasticsearchSyncService.SyncStats>> getSyncStatus(
            HttpServletRequest httpRequest) {
        
        log.debug("Getting sync status");
        
        ProductElasticsearchSyncService.SyncStats stats = syncService.getSyncStats();
        
        ApiResponse<ProductElasticsearchSyncService.SyncStats> response = 
            ApiResponse.<ProductElasticsearchSyncService.SyncStats>builder()
                .success(true)
                .message("Sync status retrieved")
                .data(stats)
                .path(httpRequest.getRequestURI())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Sync specific product",
        description = "Manually synchronize a specific product to Elasticsearch"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product synced successfully"
        )
    })
    @PostMapping("/sync/product/{productId}")
    public ResponseEntity<ApiResponse<SyncResponse>> syncProduct(
            @Parameter(description = "Product ID to sync")
            @PathVariable Long productId,
            HttpServletRequest httpRequest) {
        
        log.info("Syncing product {} to Elasticsearch", productId);
        
        try {
            syncService.syncProduct(productId);
            
            SyncResponse response = SyncResponse.builder()
                .syncType("product")
                .status("completed")
                .message("Product " + productId + " synchronized successfully")
                .productId(productId)
                .build();
            
            ApiResponse<SyncResponse> apiResponse = ApiResponse.<SyncResponse>builder()
                .success(true)
                .message("Product synced successfully")
                .data(response)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.ok(apiResponse);
            
        } catch (Exception e) {
            log.error("Failed to sync product {}", productId, e);
            throw new RuntimeException("Failed to sync product: " + e.getMessage());
        }
    }
    
    @Operation(
        summary = "Get publisher statistics",
        description = "Get statistics about the message publisher"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Publisher stats retrieved successfully"
        )
    })
    @GetMapping("/stats/publisher")
    public ResponseEntity<ApiResponse<MessagePublisher.PublisherStats>> getPublisherStats(
            HttpServletRequest httpRequest) {
        
        log.debug("Getting publisher statistics");
        
        MessagePublisher.PublisherStats stats = messagePublisher.getStats();
        
        ApiResponse<MessagePublisher.PublisherStats> response = 
            ApiResponse.<MessagePublisher.PublisherStats>builder()
                .success(true)
                .message("Publisher stats retrieved")
                .data(stats)
                .path(httpRequest.getRequestURI())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate product event data for batch storing in outbox
     */
    private List<ProductEvent.ProductCreated> generateProductEventData(String eventType, int count) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> {
                long productId = System.currentTimeMillis() + i;
                return ProductEvent.ProductCreated.builder()
                    .productId(productId)
                    .name("Sample Product " + (i + 1))
                    .description("This is a sample product created for testing batch operations - Product " + (i + 1))
                    .categories(java.util.List.of("Electronics", "Sample"))
                    .price(new java.math.BigDecimal("99.99").add(new java.math.BigDecimal(i)))
                    .sku("SAMPLE-" + String.format("%04d", i + 1))
                    .attributes(java.util.Map.of(
                        "brand", "SampleBrand",
                        "model", "Model-" + (i + 1),
                        "color", i % 2 == 0 ? "Black" : "White"
                    ))
                    .images(java.util.List.of(
                        "https://example.com/images/product-" + (i + 1) + "-1.jpg",
                        "https://example.com/images/product-" + (i + 1) + "-2.jpg"
                    ))
                    .createdAt(now.plusSeconds(i))
                    .createdBy("admin-batch-operation")
                    .build();
            })
            .toList();
    }
    
    /**
     * Response DTO for batch publish operations
     */
    @Data
    @Builder
    public static class BatchPublishResponse {
        private String topic;
        private Integer messageCount;
        private String eventType;
        private Long firstEventId;
        private Long lastEventId;
        private Boolean storedInOutbox;
    }
    
    /**
     * Response DTO for sync operations
     */
    @Data
    @Builder
    public static class SyncResponse {
        private String syncType;
        private String status;
        private String message;
        private Long productId;
    }
    
    // ==================== VIEW COUNTER ADMIN ENDPOINTS ====================
    
    @Operation(
        summary = "Get view counter statistics",
        description = "Get current statistics for the Redis-based view counter system"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "View counter stats retrieved successfully"
        )
    })
    @GetMapping("/views/stats")
    public ResponseEntity<ApiResponse<ViewCounterService.ViewStats>> getViewStats(
            HttpServletRequest httpRequest) {
        
        log.debug("Getting view counter statistics");
        
        ViewCounterService.ViewStats stats = viewCounterService.getViewStats();
        
        ApiResponse<ViewCounterService.ViewStats> response = 
            ApiResponse.<ViewCounterService.ViewStats>builder()
                .success(true)
                .message("View counter stats retrieved")
                .data(stats)
                .path(httpRequest.getRequestURI())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get view synchronization statistics",
        description = "Get current synchronization status for Redis to database sync"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Sync stats retrieved successfully"
        )
    })
    @GetMapping("/views/sync-stats")
    public ResponseEntity<ApiResponse<ViewSyncService.SyncStats>> getSyncStats(
            HttpServletRequest httpRequest) {
        
        log.debug("Getting view sync statistics");
        
        ViewSyncService.SyncStats stats = viewSyncService.getSyncStats();
        
        ApiResponse<ViewSyncService.SyncStats> response = 
            ApiResponse.<ViewSyncService.SyncStats>builder()
                .success(true)
                .message("View sync stats retrieved")
                .data(stats)
                .path(httpRequest.getRequestURI())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Trigger manual view sync",
        description = "Manually trigger synchronization of Redis view counts to database"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Manual sync completed successfully"
        )
    })
    @PostMapping("/views/sync")
    public ResponseEntity<ApiResponse<ViewSyncService.SyncResult>> triggerViewSync(
            HttpServletRequest httpRequest) {
        
        log.info("Manual view sync triggered from admin endpoint");
        
        try {
            ViewSyncService.SyncResult result = viewSyncService.triggerManualSync();
            
            ApiResponse<ViewSyncService.SyncResult> response = 
                ApiResponse.<ViewSyncService.SyncResult>builder()
                    .success(true)
                    .message("Manual view sync completed successfully")
                    .data(result)
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to trigger manual view sync", e);
            throw new RuntimeException("Failed to trigger manual view sync: " + e.getMessage());
        }
    }
    
    @Operation(
        summary = "Get product view count",
        description = "Get current view count for a specific product from Redis"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product view count retrieved successfully"
        )
    })
    @GetMapping("/views/product/{productId}")
    public ResponseEntity<ApiResponse<ProductViewInfo>> getProductViewCount(
            @Parameter(description = "Product ID")
            @PathVariable Long productId,
            HttpServletRequest httpRequest) {
        
        log.debug("Getting view count for product {}", productId);
        
        try {
            Long totalViews = viewCounterService.getProductViews(productId);
            Long dailyViews = viewCounterService.getDailyProductViews(productId);
            
            ProductViewInfo viewInfo = ProductViewInfo.builder()
                .productId(productId)
                .totalViews(totalViews)
                .dailyViews(dailyViews)
                .retrievedAt(java.time.LocalDateTime.now())
                .build();
            
            ApiResponse<ProductViewInfo> response = 
                ApiResponse.<ProductViewInfo>builder()
                    .success(true)
                    .message("Product view count retrieved successfully")
                    .data(viewInfo)
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get view count for product {}", productId, e);
            throw new RuntimeException("Failed to get product view count: " + e.getMessage());
        }
    }
    
    /**
     * Response DTO for product view information
     */
    @Data
    @Builder
    public static class ProductViewInfo {
        private Long productId;
        private Long totalViews;
        private Long dailyViews;
        private java.time.LocalDateTime retrievedAt;
    }
}
