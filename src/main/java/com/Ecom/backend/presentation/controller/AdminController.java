package com.Ecom.backend.presentation.controller;

import com.Ecom.backend.application.service.ProductElasticsearchSyncService;
import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessagePublisher;
import com.Ecom.backend.presentation.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    
    @Operation(
        summary = "Publish batch messages",
        description = "Publish a batch of test messages to the pub/sub system for testing purposes"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Batch published successfully"
        )
    })
    @PostMapping("/publish-batch")
    public ResponseEntity<ApiResponse<BatchPublishResponse>> publishBatch(
            @Parameter(description = "Topic to publish to")
            @RequestParam(defaultValue = "test-events") String topic,
            @Parameter(description = "Number of messages to publish")
            @RequestParam(defaultValue = "10") Integer messageCount,
            @Parameter(description = "Event type for the messages")
            @RequestParam(defaultValue = "TestEvent") String eventType,
            HttpServletRequest httpRequest) {
        
        log.info("Publishing batch of {} messages to topic {}", messageCount, topic);
        
        try {
            List<Message> messages = generateTestMessages(topic, eventType, messageCount);
            
            CompletableFuture<List<Message>> publishFuture = messagePublisher.publishBatch(messages);
            List<Message> publishedMessages = publishFuture.get(); // Block for demo purposes
            
            BatchPublishResponse response = BatchPublishResponse.builder()
                .topic(topic)
                .messageCount(publishedMessages.size())
                .eventType(eventType)
                .firstMessageId(publishedMessages.isEmpty() ? null : publishedMessages.get(0).getId())
                .lastMessageId(publishedMessages.isEmpty() ? null : 
                    publishedMessages.get(publishedMessages.size() - 1).getId())
                .build();
            
            ApiResponse<BatchPublishResponse> apiResponse = ApiResponse.<BatchPublishResponse>builder()
                .success(true)
                .message("Batch published successfully")
                .data(response)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.ok(apiResponse);
            
        } catch (Exception e) {
            log.error("Failed to publish batch", e);
            throw new RuntimeException("Failed to publish batch: " + e.getMessage());
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
            CompletableFuture<Void> syncFuture = syncService.performFullSync();
            
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
     * Generate test messages for batch publishing
     */
    private List<Message> generateTestMessages(String topic, String eventType, int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> {
                Map<String, String> headers = new HashMap<>();
                headers.put("test-message", "true");
                headers.put("batch-index", String.valueOf(i));
                
                String payload = String.format(
                    "{\"messageNumber\": %d, \"timestamp\": \"%s\", \"data\": \"test-data-%d\"}", 
                    i, java.time.LocalDateTime.now(), i);
                
                return Message.create(topic, eventType, payload, headers, "test-key-" + i);
            })
            .toList();
    }
    
    /**
     * Response DTO for batch publish operations
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchPublishResponse {
        private String topic;
        private Integer messageCount;
        private String eventType;
        private String firstMessageId;
        private String lastMessageId;
    }
    
    /**
     * Response DTO for sync operations
     */
    @lombok.Data
    @lombok.Builder
    public static class SyncResponse {
        private String syncType;
        private String status;
        private String message;
        private Long productId;
    }
}
