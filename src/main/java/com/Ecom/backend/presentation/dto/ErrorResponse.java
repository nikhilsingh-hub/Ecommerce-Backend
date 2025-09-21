package com.Ecom.backend.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error response for API endpoints.
 * Used for consistent error reporting across the application.
 */
@Data
@Builder
@Schema(description = "Standard error response")
public class ErrorResponse {
    
    @Schema(description = "Error code", example = "VALIDATION_ERROR")
    private String code;
    
    @Schema(description = "Error message", example = "Validation failed for request")
    private String message;
    
    @Schema(description = "Detailed error description")
    private String detail;
    
    @Schema(description = "HTTP status code", example = "400")
    private Integer status;
    
    @Schema(description = "Timestamp when error occurred")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Schema(description = "API path where error occurred")
    private String path;
    
    @Schema(description = "Request correlation ID")
    private String correlationId;
    
    @Schema(description = "Field-specific validation errors")
    private Map<String, String> fieldErrors;
    
    @Schema(description = "Additional error context")
    private Map<String, Object> context;
    
    @Schema(description = "Stack trace for debugging (only in development)")
    private List<String> stackTrace;
}
