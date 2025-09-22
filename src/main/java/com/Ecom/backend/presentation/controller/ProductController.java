package com.Ecom.backend.presentation.controller;

import com.Ecom.backend.application.dto.*;
import com.Ecom.backend.application.service.ProductSearchService;
import com.Ecom.backend.application.service.ProductService;
import com.Ecom.backend.presentation.dto.ApiResponse;
import com.Ecom.backend.presentation.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product management operations")
@Slf4j
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    private final ProductSearchService productSearchService;
    
    @Operation(
        summary = "Create a new product",
        description = "Creates a new product and publishes a ProductCreated event through the outbox pattern"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Product created successfully",
            content = @Content(schema = @Schema(implementation = ProductDto.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Product with SKU already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Creating product with SKU: {}", request.getSku());
        
        try {
            ProductDto createdProduct = productService.createProduct(request);
            
            ApiResponse<ProductDto> response = ApiResponse.<ProductDto>builder()
                .success(true)
                .message("Product created successfully")
                .data(createdProduct)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            throw new com.Ecom.backend.presentation.exception.ProductAlreadyExistsException(
                "Product with SKU " + request.getSku() + " already exists");
        }
    }
    
    @Operation(
        summary = "Get product by ID",
        description = "Retrieves a product by its unique identifier and records a view event"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product found",
            content = @Content(schema = @Schema(implementation = ProductDto.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "User ID for analytics", required = false)
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for analytics", required = false)
            @RequestParam(required = false) String sessionId,
            HttpServletRequest httpRequest) {
        
        log.debug("Fetching product with ID: {}", id);
        
        Optional<ProductDto> product = productService.getProductById(id);
        
        if (product.isPresent()) {
            // Record view event for analytics
            productService.recordProductView(id, userId, sessionId);
            
            ApiResponse<ProductDto> response = ApiResponse.<ProductDto>builder()
                .success(true)
                .message("Product retrieved successfully")
                .data(product.get())
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.ok(response);
        } else {
            throw new com.Ecom.backend.presentation.exception.ProductNotFoundException(
                "Product not found with ID: " + id);
        }
    }
    
    @Operation(
        summary = "Update a product",
        description = "Updates an existing product and publishes a ProductUpdated event"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Product updated successfully",
            content = @Content(schema = @Schema(implementation = ProductDto.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "SKU conflict with existing product",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Updating product with ID: {}", id);
        
        try {
            ProductDto updatedProduct = productService.updateProduct(id, request);
            
            ApiResponse<ProductDto> response = ApiResponse.<ProductDto>builder()
                .success(true)
                .message("Product updated successfully")
                .data(updatedProduct)
                .path(httpRequest.getRequestURI())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                throw new com.Ecom.backend.presentation.exception.ProductNotFoundException(e.getMessage());
            } else {
                throw new com.Ecom.backend.presentation.exception.ProductAlreadyExistsException(e.getMessage());
            }
        }
    }
    
    @Operation(
        summary = "Delete a product",
        description = "Deletes a product and publishes a ProductDeleted event"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "Product deleted successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {
        
        log.info("Deleting product with ID: {}", id);
        
        try {
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            throw new com.Ecom.backend.presentation.exception.ProductNotFoundException(e.getMessage());
        }
    }
    
    @Operation(
        summary = "Search products",
        description = "Search products with advanced filters and pagination using Elasticsearch"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content = @Content(schema = @Schema(implementation = ProductSearchResponse.class))
        )
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ProductSearchResponse>> searchProducts(
            @Parameter(description = "Search query")
            @RequestParam(required = false) String q,
            @Parameter(description = "Categories to filter by")
            @RequestParam(required = false) List<String> categories,
            @Parameter(description = "Minimum price")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest) {
        
        log.debug("Searching products with query: {}, categories: {}", q, categories);
        
        ProductSearchRequest searchRequest = ProductSearchRequest.builder()
            .query(q)
            .categories(categories)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .page(page)
            .size(size)
            .sortBy(sortBy)
            .sortDirection(sortDirection)
            .build();
        
        ProductSearchResponse searchResponse = productSearchService.searchProducts(searchRequest);
        
        ApiResponse<ProductSearchResponse> response = ApiResponse.<ProductSearchResponse>builder()
            .success(true)
            .message("Search completed successfully")
            .data(searchResponse)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get related products",
        description = "Find products related to a given product using Elasticsearch similarity search"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Related products found",
            content = @Content(schema = @Schema(implementation = ProductDto.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/{id}/related")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getRelatedProducts(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Maximum number of related products")
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {
        
        log.debug("Finding related products for product ID: {}", id);
        
        // First check if the product exists
        Optional<ProductDto> product = productService.getProductById(id);
        if (product.isEmpty()) {
            throw new com.Ecom.backend.presentation.exception.ProductNotFoundException(
                "Product not found with ID: " + id);
        }
        
        List<ProductDto> relatedProducts = productSearchService.findRelatedProducts(id, limit);
        
        ApiResponse<List<ProductDto>> response = ApiResponse.<List<ProductDto>>builder()
            .success(true)
            .message("Related products retrieved successfully")
            .data(relatedProducts)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get popular products",
        description = "Retrieve most popular products based on popularity score"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Popular products retrieved",
            content = @Content(schema = @Schema(implementation = ProductDto.class))
        )
    })
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getPopularProducts(
            @Parameter(description = "Maximum number of products")
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {
        
        log.debug("Getting popular products, limit: {}", limit);
        
        List<ProductDto> popularProducts = productSearchService.getPopularProducts(limit);
        
        ApiResponse<List<ProductDto>> response = ApiResponse.<List<ProductDto>>builder()
            .success(true)
            .message("Popular products retrieved successfully")
            .data(popularProducts)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get products by category",
        description = "Retrieve products filtered by categories with pagination"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Products retrieved successfully",
            content = @Content(schema = @Schema(implementation = ProductDto.class))
        )
    })
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProductsByCategories(
            @Parameter(description = "Categories to filter by", required = true)
            @RequestParam List<String> categories,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "asc") String sortDirection,
            HttpServletRequest httpRequest) {
        
        log.debug("Getting products by categories: {}", categories);
        
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? 
            Sort.Direction.ASC : Sort.Direction.DESC;
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        List<ProductDto> products = productService.getProductsByCategories(categories, pageRequest)
            .getContent();
        
        ApiResponse<List<ProductDto>> response = ApiResponse.<List<ProductDto>>builder()
            .success(true)
            .message("Products retrieved successfully")
            .data(products)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Autocomplete product names",
        description = "Get autocomplete suggestions for product names"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Suggestions retrieved successfully"
        )
    })
    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<String>>> autocompleteProducts(
            @Parameter(description = "Partial query for autocomplete", required = true)
            @RequestParam String q,
            @Parameter(description = "Maximum number of suggestions")
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {
        
        log.debug("Getting autocomplete suggestions for: {}", q);
        
        List<String> suggestions = productSearchService.getAutocompleteSuggestions(q, limit);
        
        ApiResponse<List<String>> response = ApiResponse.<List<String>>builder()
            .success(true)
            .message("Suggestions retrieved successfully")
            .data(suggestions)
            .path(httpRequest.getRequestURI())
            .build();
        
        return ResponseEntity.ok(response);
    }
}
