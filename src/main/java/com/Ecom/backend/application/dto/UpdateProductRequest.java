package com.Ecom.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for updating an existing product.
 * All fields are optional to support partial updates.
 */
@Data
@Builder
@Schema(description = "Request to update an existing product")
public class UpdateProductRequest {

    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Schema(description = "Product name", example = "Wireless Bluetooth Headphones Pro")
    private String name;

    @Size(max = 5000, message = "Product description must not exceed 5000 characters")
    @Schema(description = "Product description", example = "Updated high-quality wireless headphones with enhanced noise cancellation")
    private String description;

    @Schema(description = "Product categories", example = "[\"Electronics\", \"Audio\", \"Premium\"]")
    private List<@NotBlank(message = "Category name cannot be blank") String> categories;

    @DecimalMin(value = "0.01", message = "Product price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    @Schema(description = "Product price", example = "129.99")
    private BigDecimal price;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @Pattern(regexp = "^[A-Z0-9-_]+$", message = "SKU must contain only uppercase letters, numbers, hyphens, and underscores")
    @Schema(description = "Product SKU", example = "WBH-001-PRO")
    private String sku;

    @Schema(description = "Product attributes as key-value pairs")
    private Map<@NotBlank String, @NotBlank String> attributes;

    @Schema(description = "Product image URLs")
    private List<@Pattern(regexp = "^https?://.*", message = "Image URL must be a valid HTTP/HTTPS URL") String> images;
}
