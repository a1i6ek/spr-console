package kg.bakai.common.enums;

/**
 * Business segment of a credit product (public.product.segment_type). Stop factors with a segment check only
 * applications of products of that segment, stop factors without one check every application.
 * Lives in common: core-online (products) and stopfactor must not import each other.
 * Keep in sync with the check constraints ck_product_segment_type and ck_stop_factor_segment.
 */
public enum ProductSegment {
    /** Розничное кредитование и ИФЦ */
    RETAIL,
    /** Малый и средний бизнес */
    MSB;

    /** Segment of a scoring without a product, and of products that existed before segments (changelog 1.3). */
    public static final ProductSegment DEFAULT = RETAIL;
}
