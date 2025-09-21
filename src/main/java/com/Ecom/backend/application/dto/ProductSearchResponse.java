package com.Ecom.backend.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for product search results with pagination metadata.
 */
@Data
@Builder
@Schema(description = "Product search response with pagination information")
public class ProductSearchResponse {

    @Schema(description = "List of products matching the search criteria")
    private List<ProductDto> products;

    @Schema(description = "Total number of products matching the search criteria")
    private Long totalElements;

    @Schema(description = "Total number of pages")
    private Integer totalPages;

    @Schema(description = "Current page number (0-based)")
    private Integer currentPage;

    @Schema(description = "Number of products per page")
    private Integer pageSize;

    @Schema(description = "Whether this is the first page")
    private Boolean first;

    @Schema(description = "Whether this is the last page")
    private Boolean last;

    @Schema(description = "Whether there are more pages after this one")
    private Boolean hasNext;

    @Schema(description = "Whether there are pages before this one")
    private Boolean hasPrevious;
}
