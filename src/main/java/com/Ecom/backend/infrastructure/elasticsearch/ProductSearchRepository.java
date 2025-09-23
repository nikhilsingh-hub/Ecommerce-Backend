package com.Ecom.backend.infrastructure.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Elasticsearch repository for ProductDocument.
 * Provides advanced search capabilities beyond basic CRUD operations.
 */
@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    
    /**
     * Find products by name using fuzzy matching (simplified)
     */
    @Query("{\"match\": {\"name\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}")
    Page<ProductDocument> findByNameFuzzy(String name, Pageable pageable);
    
    /**
     * Simple fuzzy search on name field only (most reliable)
     */
    @Query("{\"fuzzy\": {\"name\": {\"value\": \"?0\", \"fuzziness\": \"AUTO\"}}}")
    Page<ProductDocument> findByNameSimpleFuzzy(String name, Pageable pageable);
    
    /**
     * Multi-field search across name, description, and SKU with fuzzy matching
     */
    @Query("{"
        + "\"multi_match\": {"
        + "  \"query\": \"?0\","
        + "  \"fields\": [\"name^3\", \"description^2\", \"sku^2\"],"
        + "  \"fuzziness\": \"AUTO\","
        + "  \"prefix_length\": 1"
        + "}}")
    Page<ProductDocument> multiFieldSearchWithFuzzy(String query, Pageable pageable);
    
    /**
     * Exact multi-field search (fallback)
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"description^2\", \"sku^2\", \"allText\"]}}")
    Page<ProductDocument> multiFieldSearch(String query, Pageable pageable);
    
    /**
     * Search with category filter
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"description^2\", \"allText\"]}}], \"filter\": [{\"terms\": {\"categories\": [\"?1\"]}}]}}")
    Page<ProductDocument> searchInCategory(String query, String category, Pageable pageable);
    
    /**
     * Search within price range
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"description^2\", \"allText\"]}}], \"filter\": [{\"range\": {\"price\": {\"gte\": ?1, \"lte\": ?2}}}]}}")
    Page<ProductDocument> searchInPriceRange(String query, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    
    /**
     * Find products by categories
     */
    Page<ProductDocument> findByCategoriesIn(List<String> categories, Pageable pageable);
    
    /**
     * Find products by price range
     */
    Page<ProductDocument> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    
    /**
     * Find products by tags
     */
    Page<ProductDocument> findByTagsIn(List<String> tags, Pageable pageable);
    
    /**
     * Find products by SKU (exact match)
     */
    ProductDocument findBySku(String sku);
    
    /**
     * Find products with high popularity score
     */
    Page<ProductDocument> findByPopularityScoreGreaterThan(Double threshold, Pageable pageable);
    
    /**
     * Find recently created products
     */
    @Query("{\"bool\": {\"filter\": [{\"range\": {\"createdAt\": {\"gte\": \"now-30d\"}}}]}, \"sort\": [{\"createdAt\": {\"order\": \"desc\"}}]}")
    Page<ProductDocument> findRecentProducts(Pageable pageable);
    
    /**
     * Autocomplete search for product names with fuzzy matching
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"name.suggest^2\"], \"fuzziness\": \"AUTO\", \"prefix_length\": 1, \"type\": \"bool_prefix\"}}")
    List<ProductDocument> autocompleteProductNames(String query);
    
    /**
     * Find similar products using More Like This query
     */
    @Query("{\"more_like_this\": {\"fields\": [\"name\", \"description\", \"categories\"], \"like\": [{\"_id\": \"?0\"}], \"min_term_freq\": 1, \"max_query_terms\": 12}}")
    Page<ProductDocument> findSimilarProducts(String productId, Pageable pageable);
}
