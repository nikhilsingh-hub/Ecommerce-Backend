package com.Ecom.backend.presentation.exception;

import com.Ecom.backend.presentation.dto.ApiResponse;
import com.Ecom.backend.presentation.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the application.
 * Provides consistent error response format and proper HTTP status codes.
 * 
 * This follows the Single Responsibility Principle by centralizing
 * all exception handling logic in one place.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @Value("${app.debug:false}")
    private boolean debugMode;
    
    /**
     * Handle product not found exceptions
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductNotFound(
            ProductNotFoundException ex, 
            HttpServletRequest request) {
        
        String correlationId = generateCorrelationId();
        log.warn("Product not found [{}]: {}", correlationId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("PRODUCT_NOT_FOUND")
            .message("Product not found")
            .detail(ex.getMessage())
            .status(HttpStatus.NOT_FOUND.value())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .message("Product not found")
            .error(ApiResponse.ErrorDetails.builder()
                .code("PRODUCT_NOT_FOUND")
                .detail(ex.getMessage())
                .build())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Handle product already exists exceptions
     */
    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductAlreadyExists(
            ProductAlreadyExistsException ex, 
            HttpServletRequest request) {
        
        String correlationId = generateCorrelationId();
        log.warn("Product already exists [{}]: {}", correlationId, ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .message("Product already exists")
            .error(ApiResponse.ErrorDetails.builder()
                .code("PRODUCT_ALREADY_EXISTS")
                .detail(ex.getMessage())
                .build())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        String correlationId = generateCorrelationId();
        log.warn("Validation error [{}]: {}", correlationId, ex.getMessage());
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });
        
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .message("Validation failed")
            .error(ApiResponse.ErrorDetails.builder()
                .code("VALIDATION_ERROR")
                .detail("One or more fields have invalid values")
                .fieldErrors(fieldErrors)
                .build())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        
        String correlationId = generateCorrelationId();
        log.warn("Illegal argument [{}]: {}", correlationId, ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .message("Invalid request")
            .error(ApiResponse.ErrorDetails.builder()
                .code("INVALID_REQUEST")
                .detail(ex.getMessage())
                .build())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        String correlationId = generateCorrelationId();
        log.error("Unexpected error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        ApiResponse.ErrorDetails.ErrorDetailsBuilder errorBuilder = ApiResponse.ErrorDetails.builder()
            .code("INTERNAL_SERVER_ERROR")
            .detail("An unexpected error occurred");
        
        // Include stack trace in debug mode
        if (debugMode) {
            errorBuilder.context(Map.of("exception", ex.getClass().getSimpleName()));
            
            List<String> stackTrace = java.util.Arrays.stream(ex.getStackTrace())
                .limit(10) // Limit stack trace length
                .map(StackTraceElement::toString)
                .toList();
            errorBuilder.context(Map.of(
                "exception", ex.getClass().getSimpleName(),
                "stackTrace", stackTrace
            ));
        }
        
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .message("Internal server error")
            .error(errorBuilder.build())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handle runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {
        
        String correlationId = generateCorrelationId();
        log.error("Runtime error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        // Check if it's a known business exception that wasn't caught by specific handlers
        if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
            return handleProductNotFound(new ProductNotFoundException(ex.getMessage()), request);
        }
        
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .message("Operation failed")
            .error(ApiResponse.ErrorDetails.builder()
                .code("OPERATION_FAILED")
                .detail(debugMode ? ex.getMessage() : "An error occurred while processing your request")
                .build())
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Generate a unique correlation ID for request tracking
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
