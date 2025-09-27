package com.Ecom.backend.application.service.ProductService;

import com.Ecom.backend.application.dto.ProductDto;
import com.Ecom.backend.application.dto.ProductSearchRequest;
import com.Ecom.backend.application.dto.ProductSearchResponse;
import com.Ecom.backend.application.mapper.ProductMapper;
import com.Ecom.backend.application.service.ViewService.ViewCounterService;
import com.Ecom.backend.domain.entity.Product;
import com.Ecom.backend.infrastructure.elasticsearch.ProductDocument;
import com.Ecom.backend.infrastructure.elasticsearch.ProductSearchRepository;
import com.Ecom.backend.infrastructure.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Service for advanced product search capabilities using Elasticsearch.
 * Provides fallback to MySQL when Elasticsearch is unavailable.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductSearchService {
    
    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ViewCounterService viewCounterService;
    
    @Value("${product.search.popularity-threshold:10.0}")
    private Double popularityThreshold;

    /**
     * Get product by ID
     *
     * @param productId Product ID
     * @return Product DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<ProductDto> getProductById(Long productId) {

        try {
            log.debug("Fetching product with ID: {}", productId);
            Optional<ProductDto> product =  productRepository.findById(productId)
                    .map(productMapper::toDto);
            if (product.isPresent()){
                // Record view event for analytics
                viewCounterService.incrementProductViews(productId);
            }
            return product;
        } catch (Exception e) {
            log.warn("Elasticsearch search failed, falling back to MySQL: {}", e.getMessage());
            return Optional.empty();
        }

    }

    /**
     * Perform advanced product search with filters and pagination
     * 
     * @param searchRequest Search parameters and filters
     * @return Search results with pagination metadata
     */

    public ProductSearchResponse searchProducts(ProductSearchRequest searchRequest) {
        log.debug("Searching products with request: {}", searchRequest);
        
        try {
            Page<ProductDocument> searchResults = performElasticsearchQuery(searchRequest);
            return buildSearchResponse(searchResults, searchRequest); 
            
        } catch (Exception e) {
            log.warn("Elasticsearch search failed, falling back to MySQL: {}", e.getMessage());
            return fallbackToMySQLSearch(searchRequest);
        }
    }
    
    /**
     * Perform the actual Elasticsearch query based on search request
     */
    private Page<ProductDocument> performElasticsearchQuery(ProductSearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);

        // Default filters
        List<String> categories = searchRequest.getCategories() != null ? searchRequest.getCategories() : Collections.emptyList();
        BigDecimal minPrice = searchRequest.getMinPrice() != null ? searchRequest.getMinPrice() : BigDecimal.ZERO;
        BigDecimal maxPrice = searchRequest.getMaxPrice() != null ? searchRequest.getMaxPrice() : BigDecimal.valueOf(Double.MAX_VALUE);

        if (!StringUtils.hasText(searchRequest.getQuery())) {
            if (!categories.isEmpty()) {
                return searchRepository.findByCategoriesIn(categories, pageable);
            } else {
                return searchRepository.findByPriceBetween(minPrice, maxPrice, pageable);
            }
        } else {
            // Search with multi-field + fuzzy + filters
            return searchRepository.searchProducts(
                    searchRequest.getQuery(),
                    categories,
                    minPrice,
                    maxPrice,
                    pageable
            );
        }
    }


    /**
     * Create pageable object from search request
     */
    private Pageable createPageable(ProductSearchRequest searchRequest) {
        Sort sort = createSort(searchRequest.getSortBy(), searchRequest.getSortDirection());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }
    
    /**
     * Create sort object from sort parameters
     */
    private Sort createSort(String sortBy, String sortDirection) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? 
            Sort.Direction.ASC : Sort.Direction.DESC;
        
        return switch (sortBy.toLowerCase()) {
            case "name" -> Sort.by(direction, "name.keyword");
            case "price" -> Sort.by(direction, "price");
            case "createdat" -> Sort.by(direction, "createdAt");
            case "popularityscore" -> Sort.by(direction, "popularityScore");
            default -> Sort.by(Sort.Direction.DESC, "popularityScore", "createdAt");
        };
    }
    
    
    /**
     * Build search response from Elasticsearch results
     */
    private ProductSearchResponse buildSearchResponse(Page<ProductDocument> searchResults, ProductSearchRequest searchRequest) {
        List<ProductDto> productDtos = searchResults.getContent().stream()
            .map(this::convertDocumentToDto)
            .collect(Collectors.toList());
        
        return ProductSearchResponse.builder()
            .products(productDtos)
            .totalElements(searchResults.getTotalElements())
            .totalPages(searchResults.getTotalPages())
            .currentPage(searchResults.getNumber())
            .pageSize(searchResults.getSize())
            .first(searchResults.isFirst())
            .last(searchResults.isLast())
            .hasNext(searchResults.hasNext())
            .hasPrevious(searchResults.hasPrevious())
            .build();
    }
    
    /**
     * Fallback to MySQL search when Elasticsearch is unavailable
     */
    private ProductSearchResponse fallbackToMySQLSearch(ProductSearchRequest searchRequest) {
        log.info("Using MySQL fallback for search");
        
        Pageable pageable = createPageable(searchRequest);
        
        Page<Product> mysqlResults = productRepository.searchWithFilters(
            searchRequest.getQuery(),
            searchRequest.getCategories(),
            searchRequest.getMinPrice(),
            searchRequest.getMaxPrice(),
            pageable
        );
        
        List<ProductDto> productDtos = mysqlResults.getContent().stream()
            .map(productMapper::toDto)
            .collect(Collectors.toList());
        
        return ProductSearchResponse.builder()
            .products(productDtos)
            .totalElements(mysqlResults.getTotalElements())
            .totalPages(mysqlResults.getTotalPages())
            .currentPage(mysqlResults.getNumber())
            .pageSize(mysqlResults.getSize())
            .first(mysqlResults.isFirst())
            .last(mysqlResults.isLast())
            .hasNext(mysqlResults.hasNext())
            .hasPrevious(mysqlResults.hasPrevious())
            .build();
    }
    
    /**
     * Find related products using Elasticsearch More Like This query
     * 
     * @param productId Product ID to find related products for
     * @param limit Maximum number of related products to return
     * @return List of related product DTOs
     */
    public List<ProductDto> findRelatedProducts(Long productId, int limit) {
        log.debug("Finding related products for product ID: {}", productId);
        
        try {
            Pageable pageable = PageRequest.of(0, limit);
            Page<ProductDocument> relatedProducts = searchRepository.findSimilarProducts(
                productId.toString(), pageable);
            
            return relatedProducts.getContent().stream()
                .map(this::convertDocumentToDto)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Elasticsearch related products search failed, falling back to MySQL: {}", e.getMessage());
            return fallbackToMySQLRelatedProducts(productId, limit);
        }
    }
    
    /**
     * Autocomplete suggestions for product names
     * 
     * @param query Partial query string
     * @param limit Maximum number of suggestions
     * @return List of product name suggestions
     */
    public List<String> getAutocompleteSuggestions(String query, int limit) {
        log.debug("Getting autocomplete suggestions for query: {}", query);

        try {
            List<ProductDocument> suggestions = searchRepository.autocompleteProductNames(query, PageRequest.of(0, limit));
            return suggestions.stream()
                .limit(limit)
                .map(ProductDocument::getName)
                .distinct()
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Elasticsearch autocomplete failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get popular products
     * 
     * @param limit Maximum number of products to return
     * @return List of popular product DTOs
     */
    public List<ProductDto> getPopularProducts(int limit) {
        log.debug("Getting popular products, limit: {}", limit);
        
        try {
            Pageable pageable = PageRequest.of(0, limit);
            Page<ProductDocument> popularProducts = searchRepository.findByPopularityScoreGreaterThan(popularityThreshold, pageable);
            
            return popularProducts.getContent().stream()
                .map(this::convertDocumentToDto)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Elasticsearch popular products search failed, falling back to MySQL: {}", e.getMessage());
            return fallbackToMySQLPopularProducts(limit);
        }
    }
    
    /**
     * Convert ProductDocument to ProductDto
     */
    private ProductDto convertDocumentToDto(ProductDocument document) {
        return ProductDto.builder()
            .id(document.getProductId())
            .name(document.getName())
            .description(document.getDescription())
            .categories(document.getCategories())
            .price(document.getPrice())
            .sku(document.getSku())
            .attributes(document.getAttributes())
            .images(document.getImages())
            .createdAt(document.getCreatedAt())
            .updatedAt(document.getUpdatedAt())
            .clickCount(document.getClickCount())
            .purchaseCount(document.getPurchaseCount())
            .popularityScore(document.getPopularityScore())
            .build();
    }
    
    /**
     * Fallback for related products using MySQL
     */
    private List<ProductDto> fallbackToMySQLRelatedProducts(Long productId, int limit) {
        Optional<Product> product = productRepository.findById(productId);
        if (product.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Product> relatedProducts = productRepository.findRelatedProductsByCategory(
            productId, product.get().getCategories(), 
            PageRequest.of(0, limit));
        
        return productMapper.toDtoList(relatedProducts);
    }
    
    /**
     * Fallback for popular products using MySQL
     */
    private List<ProductDto> fallbackToMySQLPopularProducts(int limit) {
        List<Product> popularProducts = productRepository.findTopByPopularity(
            PageRequest.of(0, limit));
        return productMapper.toDtoList(popularProducts);
    }
}
