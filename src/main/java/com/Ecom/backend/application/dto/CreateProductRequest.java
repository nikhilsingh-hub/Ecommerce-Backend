package com.Ecom.backend.application.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a new product.
 * Contains validation annotations following the principle of fail-fast.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    @Size(max = 5000, message = "Product description must not exceed 5000 characters")
    private String description;

    @NotEmpty(message = "At least one category is required")
    private List<@NotBlank(message = "Category name cannot be blank") String> categories;

    @NotNull(message = "Product price is required")
    @DecimalMin(value = "0.01", message = "Product price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    private BigDecimal price;

    @NotBlank(message = "Product SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @Pattern(regexp = "^[A-Z0-9-_]+$", message = "SKU must contain only uppercase letters, numbers, hyphens, and underscores")
    private String sku;

    private Map<@NotBlank String, @NotBlank String> attributes;

    private List<@Pattern(regexp = "^https?://.*", message = "Image URL must be a valid HTTP/HTTPS URL") String> images;
}
