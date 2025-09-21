package com.Ecom.backend.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper for all REST endpoints.
 * Provides consistent response structure across the application.
 * 
 * @param <T> Type of the response data
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {
    
    @Schema(description = "Indicates if the operation was successful", example = "true")
    @Builder.Default
    private Boolean success = true;
    
    @Schema(description = "Human-readable message describing the result", example = "Operation completed successfully")
    private String message;
    
    @Schema(description = "Response data payload")
    private T data;
    
    @Schema(description = "Error details in case of failure")
    private ErrorDetails error;
    
    @Schema(description = "Timestamp of the response")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Schema(description = "API path that generated this response")
    private String path;
    
    @Schema(description = "Request correlation ID for tracking")
    private String correlationId;
    
    /**
     * Create a successful response with data
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    /**
     * Create a successful response without data
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .build();
    }
    
    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String message, ErrorDetails error) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .error(error)
            .build();
    }
    
    /**
     * Error details for failed responses
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Error details")
    public static class ErrorDetails {
        
        @Schema(description = "Error code", example = "PRODUCT_NOT_FOUND")
        private String code;
        
        @Schema(description = "Detailed error message", example = "Product with ID 123 was not found")
        private String detail;
        
        @Schema(description = "Field-specific validation errors")
        private java.util.Map<String, String> fieldErrors;
        
        @Schema(description = "Additional error context")
        private java.util.Map<String, Object> context;
    }
}
