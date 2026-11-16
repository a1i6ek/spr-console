package kg.bakai.stopfactor.repository.projection;

import kg.bakai.common.enums.ProductSegment;

/**
 * Credit product of SPR (table public.product). Read with a native query: the product table belongs to SPR,
 * the stop factor module does not map it as its own entity.
 */
public interface ProductProjection {
    Long getId();

    String getName();

    String getDescription();

    /** SPR's own public.product.segment_type: RETAIL, BUSINESS, IFC, ... */
    String getSegmentType();

    default ProductSegment getSegment() {
        return ProductSegment.fromSegmentType(getSegmentType());
    }
}
