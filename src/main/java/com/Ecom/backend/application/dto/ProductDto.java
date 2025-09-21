package com.Ecom.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product Data Transfer Object for API responses.
 * Immutable DTO following the principle of separation of concerns.
 */
@Data
@Builder
@Schema(description = "Product information")
public class ProductDto {

    @Schema(description = "Product unique identifier", example = "1")
    private Long id;

    @Schema(description = "Product name", example = "Wireless Bluetooth Headphones")
    private String name;

    @Schema(description = "Product description", example = "High-quality wireless headphones with noise cancellation")
    private String description;

    @Schema(description = "Product categories", example = "[\"Electronics\", \"Audio\"]")
    private List<String> categories;

    @Schema(description = "Product price", example = "99.99")
    private BigDecimal price;

    @Schema(description = "Product SKU", example = "WBH-001")
    private String sku;

    @Schema(description = "Product attributes as key-value pairs")
    private Map<String, String> attributes;

    @Schema(description = "Product image URLs")
    private List<String> images;

    @Schema(description = "Product creation timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Product last update timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "Number of times product was clicked")
    private Long clickCount;

    @Schema(description = "Number of times product was purchased")
    private Long purchaseCount;

    @Schema(description = "Calculated popularity score")
    private Double popularityScore;
}
