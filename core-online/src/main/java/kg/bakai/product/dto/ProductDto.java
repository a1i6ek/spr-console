package kg.bakai.product.dto;

import kg.bakai.common.enums.ProductSegment;

import java.time.LocalDateTime;

/**
 * @param segmentType SPR's own segment of the product: RETAIL, BUSINESS, IFC, ...
 * @param segment     which stop factors check applications of this product: BUSINESS is MSB, the others RETAIL
 */
public record ProductDto(
        Long id,
        String name,
        String description,
        String segmentType,
        ProductSegment segment,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {
}
