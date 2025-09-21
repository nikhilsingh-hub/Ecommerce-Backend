package com.Ecom.backend.presentation.exception;

/**
 * Exception thrown when a product is not found.
 * This is a business exception that results in a 404 HTTP status.
 */
public class ProductNotFoundException extends RuntimeException {
    
    public ProductNotFoundException(String message) {
        super(message);
    }
    
    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
