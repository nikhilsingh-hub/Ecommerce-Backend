package com.Ecom.backend.infrastructure.repository;

import com.Ecom.backend.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Product entity.
 * Extends JpaRepository to inherit basic CRUD operations and adds custom query methods.
 * Follows the Repository pattern from DDD.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find product by SKU (unique identifier)
     */
    Optional<Product> findBySku(String sku);

    /**
     * Check if product exists by SKU
     */
    boolean existsBySku(String sku);

    /**
     * Find products by category with pagination
     */
    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c IN :categories")
    Page<Product> findByCategoriesIn(@Param("categories") List<String> categories, Pageable pageable);
    /**
     * Find all products with eagerly loaded categories for Elasticsearch sync
     * Uses JOIN FETCH to avoid LazyInitializationException
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories")
    Page<Product> findAllWithCategories(Pageable pageable);

    /**
     * Find product by ID with eagerly loaded categories
     * Note: Other collections (attributes, images) will be loaded within transaction context
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.categories WHERE p.id = :id")
    Optional<Product> findByIdWithCategories(@Param("id") Long id);
    /**
     * Complex search with multiple filters
     */
    @Query("SELECT p FROM Product p LEFT JOIN p.categories c WHERE " +
           "(:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:categories IS NULL OR c IN :categories) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchWithFilters(
        @Param("query") String query,
        @Param("categories") List<String> categories,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable
    );

    /**
     * Find related products by category and popularity
     * Excludes the current product and orders by popularity score
     */
    @Query("SELECT p FROM Product p JOIN p.categories c WHERE " +
           "p.id != :productId AND c IN :categories " +
           "ORDER BY p.popularityScore DESC, p.purchaseCount DESC")
    List<Product> findRelatedProductsByCategory(
        @Param("productId") Long productId,
        @Param("categories") List<String> categories,
        Pageable pageable
    );

    /**
     * Find top products by popularity
     */
    @Query("SELECT p FROM Product p ORDER BY p.popularityScore DESC, p.purchaseCount DESC")
    List<Product> findTopByPopularity(Pageable pageable);

    /**
     * Increment click count for a product
     */
    @Modifying
    @Query("UPDATE Product p SET p.clickCount = p.clickCount + 1 WHERE p.id = :productId")
    int incrementClickCount(@Param("productId") Long productId);

    /**
     * Increment purchase count for a product
     */
    @Modifying
    @Query("UPDATE Product p SET p.purchaseCount = p.purchaseCount + 1 WHERE p.id = :productId")
    int incrementPurchaseCount(@Param("productId") Long productId);

    /**
     * Find products with low stock or requiring attention
     * This could be extended based on business rules
     */
    @Query("SELECT p FROM Product p WHERE p.popularityScore > :threshold ORDER BY p.updatedAt DESC")
    List<Product> findPopularProducts(@Param("threshold") Double threshold);

    /**
     * Count products by category
     */
    @Query("SELECT c, COUNT(p) FROM Product p JOIN p.categories c GROUP BY c")
    List<Object[]> countProductsByCategory();


    /**
     * Find products within price range
     */
    Page<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    /**
     * Search products by name or description (case-insensitive)
     */
    @Query("SELECT p FROM Product p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Product> searchByNameOrDescription(@Param("query") String query, Pageable pageable);
}
