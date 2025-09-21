package com.Ecom.backend.presentation.exception;

/**
 * Exception thrown when trying to create a product with a SKU that already exists.
 * This is a business exception that results in a 409 HTTP status.
 */
public class ProductAlreadyExistsException extends RuntimeException {
    
    public ProductAlreadyExistsException(String message) {
        super(message);
    }
    
    public ProductAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
