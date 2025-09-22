package com.Ecom.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for product search results with pagination metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {

    private List<ProductDto> products;

    private Long totalElements;

    private Integer totalPages;

    private Integer currentPage;

    private Integer pageSize;

    private Boolean first;

    private Boolean last;

    private Boolean hasNext;

    private Boolean hasPrevious;
}
