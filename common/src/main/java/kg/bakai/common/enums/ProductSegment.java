package kg.bakai.common.enums;

/**
 * Segment a stop factor applies to. Stop factors with a segment check only applications of products of that segment,
 * stop factors without one check every application.
 * Lives in common: core-online (products) and stopfactor must not import each other.
 */
public enum ProductSegment {
    /** Розничное кредитование и ИФЦ */
    RETAIL,
    /** Малый и средний бизнес */
    MSB;

    /** Segment of a scoring without a product. */
    public static final ProductSegment DEFAULT = RETAIL;

    /**
     * The segment of a product from SPR's own public.product.segment_type (RETAIL, BUSINESS, IFC, ...): business
     * products are MSB, every other product is RETAIL. SPR's column is only read, never changed.
     */
    public static ProductSegment fromSegmentType(String segmentType) {
        return "BUSINESS".equals(segmentType) || "MSB".equals(segmentType) ? MSB : RETAIL;
    }
}
