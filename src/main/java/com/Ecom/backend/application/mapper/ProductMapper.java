package com.Ecom.backend.application.mapper;

import com.Ecom.backend.application.dto.CreateProductRequest;
import com.Ecom.backend.application.dto.ProductDto;
import com.Ecom.backend.application.dto.UpdateProductRequest;
import com.Ecom.backend.domain.entity.Product;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for converting between Product entities and DTOs.
 * Follows the Single Responsibility Principle by focusing only on mapping concerns.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ProductMapper {

    /**
     * Convert Product entity to ProductDto
     */
    ProductDto toDto(Product product);

    /**
     * Convert list of Product entities to list of ProductDtos
     */
    List<ProductDto> toDtoList(List<Product> products);

    /**
     * Convert CreateProductRequest to Product entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "clickCount", constant = "0L")
    @Mapping(target = "purchaseCount", constant = "0L")
    @Mapping(target = "popularityScore", constant = "0.0")
    Product toEntity(CreateProductRequest request);

    /**
     * Update existing Product entity with data from UpdateProductRequest
     * Only non-null fields from the request will be mapped
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "clickCount", ignore = true)
    @Mapping(target = "purchaseCount", ignore = true)
    @Mapping(target = "popularityScore", ignore = true)
    void updateEntity(@MappingTarget Product product, UpdateProductRequest request);

    /**
     * Custom mapping method to handle SKU uniqueness validation
     */
    @AfterMapping
    default void afterMappingToEntity(@MappingTarget Product product, CreateProductRequest request) {
        if (product.getSku() != null) {
            product.setSku(product.getSku().toUpperCase().trim());
        }
    }

    /**
     * Custom mapping method for updates to handle SKU normalization
     */
    @AfterMapping
    default void afterMappingUpdate(@MappingTarget Product product, UpdateProductRequest request) {
        if (request.getSku() != null) {
            product.setSku(request.getSku().toUpperCase().trim());
        }
        // Recalculate popularity score after any update
        product.calculatePopularityScore();
    }
}
