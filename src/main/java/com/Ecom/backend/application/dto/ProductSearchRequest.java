package com.Ecom.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for product search with filters and pagination.
 */
@Data
@Builder
@Schema(description = "Product search request with filters and pagination")
public class ProductSearchRequest {

    @Schema(description = "Search query for product name and description", example = "wireless headphones")
    private String query;

    @Schema(description = "Filter by categories", example = "[\"Electronics\", \"Audio\"]")
    private List<String> categories;

    @Schema(description = "Minimum price filter", example = "50.00")
    private BigDecimal minPrice;

    @Schema(description = "Maximum price filter", example = "200.00")
    private BigDecimal maxPrice;

    @Schema(description = "Page number (0-based)", example = "0")
    @Min(value = 0, message = "Page number must be non-negative")
    @Builder.Default
    private Integer page = 0;

    @Schema(description = "Page size", example = "20")
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    @Builder.Default
    private Integer size = 20;

    @Schema(description = "Sort field", example = "name", allowableValues = {"name", "price", "createdAt", "popularityScore"})
    @Builder.Default
    private String sortBy = "createdAt";

    @Schema(description = "Sort direction", example = "desc", allowableValues = {"asc", "desc"})
    @Builder.Default
    private String sortDirection = "desc";
}
