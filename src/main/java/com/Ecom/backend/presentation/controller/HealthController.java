package com.Ecom.backend.presentation.controller;

import com.Ecom.backend.application.service.OutboxEventService;
import com.Ecom.backend.application.service.ProductElasticsearchSyncService;
import com.Ecom.backend.infrastructure.pubsub.Broker.InMemoryMessageBroker;
import com.Ecom.backend.infrastructure.pubsub.Interface.MessagePublisher;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import com.Ecom.backend.presentation.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller providing system status information.
 * Includes checks for all major components: database, Elasticsearch, pub/sub, etc.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "System health and status operations")
@Slf4j
@RequiredArgsConstructor
public class HealthController {
    
    private final DataSource dataSource;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductRepository productRepository;
    private final InMemoryMessageBroker messageBroker;
    private final MessagePublisher messagePublisher;
    private final OutboxEventService outboxEventService;
    private final ProductElasticsearchSyncService syncService;
    
    @Operation(
        summary = "Basic health check",
        description = "Simple health check that returns OK if the application is running"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Application is healthy"
        )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<HealthStatus>> health(HttpServletRequest httpRequest) {
        
        HealthStatus status = HealthStatus.builder()
            .status("UP")
            .timestamp(LocalDateTime.now())
            .version("1.0.0")
            .environment("development")
            .build();
        
        ApiResponse<HealthStatus> response = ApiResponse.<HealthStatus>builder()
            .success(true)
            .message("Application is healthy")
            .data(status)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Detailed health check",
        description = "Comprehensive health check including all system components"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Detailed health information"
        )
    })
    @GetMapping("/detailed")
    public ResponseEntity<ApiResponse<DetailedHealthStatus>> detailedHealth(
            HttpServletRequest httpRequest) {
        
        log.debug("Performing detailed health check");
        
        Map<String, ComponentHealth> components = new HashMap<>();
        
        // Check database
        components.put("database", checkDatabase());
        
        // Check Elasticsearch
        components.put("elasticsearch", checkElasticsearch());
        
        // Check message broker
        components.put("messageBroker", checkMessageBroker());
        
        // Check outbox service
        components.put("outboxService", checkOutboxService());
        
        // Check sync service
        components.put("syncService", checkSyncService());
        
        // Determine overall status
        boolean allHealthy = components.values().stream()
            .allMatch(health -> "UP".equals(health.getStatus()));
        
        DetailedHealthStatus status = DetailedHealthStatus.builder()
            .status(allHealthy ? "UP" : "DOWN")
            .timestamp(LocalDateTime.now())
            .version("1.0.0")
            .environment("development")
            .components(components)
            .build();
        
        ApiResponse<DetailedHealthStatus> response = ApiResponse.<DetailedHealthStatus>builder()
            .success(true)
            .message("Detailed health check completed")
            .data(status)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get system metrics",
        description = "Get various system metrics and statistics"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "System metrics retrieved"
        )
    })
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<SystemMetrics>> getMetrics(
            HttpServletRequest httpRequest) {
        
        log.debug("Collecting system metrics");
        
        try {
            // Collect various metrics
            long productCount = productRepository.count();
            MessagePublisher.PublisherStats publisherStats = messagePublisher.getStats();
            OutboxEventService.OutboxStats outboxStats = outboxEventService.getStats();
            ProductElasticsearchSyncService.SyncStats syncStats = syncService.getSyncStats();
            InMemoryMessageBroker.BrokerStats brokerStats = messageBroker.getStats();
            
            SystemMetrics metrics = SystemMetrics.builder()
                .timestamp(LocalDateTime.now())
                .productCount(productCount)
                .publisherStats(publisherStats)
                .outboxStats(outboxStats)
                .syncStats(syncStats)
                .brokerStats(brokerStats)
                .build();
            
            ApiResponse<SystemMetrics> response = ApiResponse.<SystemMetrics>builder()
                .success(true)
                .message("System metrics collected")
                .data(metrics)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to collect metrics", e);
            throw new RuntimeException("Failed to collect metrics: " + e.getMessage());
        }
    }
    
    /**
     * Check database connectivity
     */
    private ComponentHealth checkDatabase() {
        try {
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(5); // 5 second timeout
                if (isValid) {
                    return ComponentHealth.builder()
                        .status("UP")
                        .details(Map.of(
                            "database", connection.getCatalog(),
                            "url", connection.getMetaData().getURL()
                        ))
                        .build();
                } else {
                    return ComponentHealth.builder()
                        .status("DOWN")
                        .details(Map.of("error", "Connection validation failed"))
                        .build();
                }
            }
        } catch (Exception e) {
            return ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Check Elasticsearch connectivity
     */
    private ComponentHealth checkElasticsearch() {
        try {
            // Try to get cluster health
            boolean isConnected = elasticsearchOperations.indexOps(
                com.Ecom.backend.infrastructure.elasticsearch.ProductDocument.class).exists();
            
            return ComponentHealth.builder()
                .status("UP")
                .details(Map.of(
                    "indexExists", isConnected
                ))
                .build();
                
        } catch (Exception e) {
            return ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Check message broker
     */
    private ComponentHealth checkMessageBroker() {
        try {
            InMemoryMessageBroker.BrokerStats stats = messageBroker.getStats();
            
            return ComponentHealth.builder()
                .status("UP")
                .details(Map.of(
                    "totalTopics", stats.totalTopics(),
                    "totalConsumerGroups", stats.totalConsumerGroups(),
                    "totalMessages", stats.totalMessages()
                ))
                .build();
                
        } catch (Exception e) {
            return ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Check outbox service
     */
    private ComponentHealth checkOutboxService() {
        try {
            OutboxEventService.OutboxStats stats = outboxEventService.getStats();
            
            return ComponentHealth.builder()
                .status("UP")
                .details(Map.of(
                    "unprocessedEvents", stats.unprocessedEventCount(),
                    "totalEventTypes", stats.totalEventTypes()
                ))
                .build();
                
        } catch (Exception e) {
            return ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Check sync service
     */
    private ComponentHealth checkSyncService() {
        try {
            ProductElasticsearchSyncService.SyncStats stats = syncService.getSyncStats();
            
            return ComponentHealth.builder()
                .status(stats.inSync() ? "UP" : "WARN")
                .details(Map.of(
                    "mysqlProductCount", stats.mysqlProductCount(),
                    "elasticsearchProductCount", stats.elasticsearchProductCount(),
                    "inSync", stats.inSync()
                ))
                .build();
                
        } catch (Exception e) {
            return ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Basic health status
     */
    @lombok.Data
    @lombok.Builder
    public static class HealthStatus {
        private String status;
        private LocalDateTime timestamp;
        private String version;
        private String environment;
    }
    
    /**
     * Detailed health status with component breakdown
     */
    @lombok.Data
    @lombok.Builder
    public static class DetailedHealthStatus {
        private String status;
        private LocalDateTime timestamp;
        private String version;
        private String environment;
        private Map<String, ComponentHealth> components;
    }
    
    /**
     * Individual component health
     */
    @lombok.Data
    @lombok.Builder
    public static class ComponentHealth {
        private String status;
        private Map<String, Object> details;
    }
    
    /**
     * System metrics
     */
    @lombok.Data
    @lombok.Builder
    public static class SystemMetrics {
        private LocalDateTime timestamp;
        private Long productCount;
        private MessagePublisher.PublisherStats publisherStats;
        private OutboxEventService.OutboxStats outboxStats;
        private ProductElasticsearchSyncService.SyncStats syncStats;
        private InMemoryMessageBroker.BrokerStats brokerStats;
    }
}
