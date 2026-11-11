package kg.bakai.stopfactor.entity;

import jakarta.persistence.*;
import kg.bakai.common.entity.BaseEntity;
import kg.bakai.common.enums.ProductSegment;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A group of stop factors, the versioning unit: if ANY stop factor of a group changes, the group version is closed
 * (valid_to) and a NEW group version is created with ALL stop factors copied, so a check keeps the exact setup it
 * ran with. The group's description, status, order, products and segment version with its stop factors; its name
 * is derived from them. A group is just grouping: it has no name of its own.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_group", schema = "stop_factors")
public class StopFactorGroup extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_group_id_seq")
    @SequenceGenerator(name = "stop_factor_group_id_seq", sequenceName = "stop_factor_group_id_generator", schema = "stop_factors", allocationSize = 1)
    private Long id;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "order_number")
    private int orderNumber;

    @Column(name = "root_id", updatable = false)
    private Long rootId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    /** The stop factors of this version, in order. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNumber")
    private List<StopFactor> factors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "stop_factor_group_product",
            schema = "stop_factors",
            joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "product_id", nullable = false)
    private Set<Long> productIds = new HashSet<>();

    /** Segment of the products it applies to, null - every segment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "segment", length = 32)
    private ProductSegment segment;

    /** The display name: like its only stop factor, or like its metric when the group has several of them. */
    public String getName() {
        return factors.size() == 1
                ? factors.getFirst().getName()
                : factors.getFirst().getMetric().getTitle();
    }

    public boolean isCurrent() {
        return validTo == null;
    }

    /**
     * Whether an application of this product is checked by this group: the segment must match (or be empty)
     * and the product must be one of its products (or it has none). A scoring without product (productId null)
     * ignores product limits, as before products existed.
     */
    public boolean appliesTo(Long productId, ProductSegment productSegment) {
        boolean segmentMatches = segment == null || segment == productSegment;
        boolean productMatches = productId == null || productIds.isEmpty() || productIds.contains(productId);
        return segmentMatches && productMatches;
    }

    /** Removes every stop factor, to set the stop factors of a new version anew. */
    public void removeFactors() {
        factors.clear();
    }

    /** Adds a stop factor after the existing ones. */
    public StopFactor addFactor() {
        StopFactor factor = new StopFactor(this, factors.size() + 1);
        factors.add(factor);
        return factor;
    }

    public Long getGroupId() {
        return rootId != null ? rootId : id;
    }

    public StopFactorGroup newVersion(LocalDateTime now, String reason) {
        StopFactorGroup next = new StopFactorGroup();
        next.rootId = getGroupId();                  // always the FIRST version, never the previous one
        next.versionNumber = this.versionNumber + 1;
        next.validFrom = now;
        next.changeReason = reason;
        next.description = this.description;
        next.isActive = this.isActive;
        next.orderNumber = this.orderNumber;
        this.factors.forEach(factor -> next.factors.add(factor.copyTo(next)));
        next.productIds = new HashSet<>(this.productIds);
        next.segment = this.segment;
        return next;
    }
}
