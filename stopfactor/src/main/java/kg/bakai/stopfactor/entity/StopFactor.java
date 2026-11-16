package kg.bakai.stopfactor.entity;

import jakarta.persistence.*;
import kg.bakai.common.entity.BaseEntity;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A stop factor in a box ({@link StopFactorGroup}): its metric with param values, its conditions (when the
 * application is REJECTED or goes to the UNDERWRITER) and which applications it checks (segment, products). Never
 * changed once saved: a change makes the next box with copies of all stop factors, so a check keeps pointing to the
 * row it ran with. {@link #factorId} is the stop factor in every box; {@link #versionNumber} its own version, copied as
 * it is when another stop factor changes.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stop_factor", schema = "stop_factors")
public class StopFactor extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_id_seq")
    @SequenceGenerator(name = "stop_factor_id_seq", sequenceName = "stop_factor_id_generator", schema = "public", allocationSize = 1)
    private Long id;

    /** The box it is in. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private StopFactorGroup group;

    /** The stop factor: the same in every box; what the console calls it by. */
    @Column(name = "factor_id", nullable = false, updatable = false)
    private Long factorId;

    /** Its own version: increased when this stop factor changes. */
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** Check order. */
    @Column(name = "order_number", nullable = false)
    private int orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_id", nullable = false)
    private StopFactorMetric metric;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    /** An inactive stop factor is not checked. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Segment of the products it checks, null - every segment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "segment", length = 32)
    private ProductSegment segment;

    /** Values of the metric params, e.g. {"periodFromMonth": 7, "periodToMonth": 12}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> params = new LinkedHashMap<>();

    /** Products it is limited to, empty - every product of its segment. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "stop_factor_product", schema = "stop_factors", joinColumns = @JoinColumn(name = "stop_factor_id"))
    @Column(name = "product_id", nullable = false)
    private Set<Long> productIds = new HashSet<>();

    /** At most one REJECTED and one UNDERWRITER condition; none - the stop factor is measured but decides nothing. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "stopFactor", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("result")
    private List<StopFactorCondition> conditions = new ArrayList<>();

    /** A new stop factor, version 1, in a box. */
    public static StopFactor first(StopFactorGroup group, long factorId, int orderNumber) {
        StopFactor factor = new StopFactor();
        factor.group = group;
        factor.factorId = factorId;
        factor.versionNumber = 1;
        factor.orderNumber = orderNumber;
        return factor;
    }

    /** A copy of this stop factor, the same version, in the next box; the caller changes it if it is the one changed. */
    public StopFactor copyTo(StopFactorGroup box) {
        StopFactor copy = first(box, factorId, orderNumber);
        copy.versionNumber = versionNumber;
        copy.metric = metric;
        copy.name = name;
        copy.description = description;
        copy.active = active;
        copy.segment = segment;
        copy.params = new LinkedHashMap<>(params);
        copy.productIds = new HashSet<>(productIds);
        conditions.forEach(c -> copy.addCondition(c.getResult(), c.getOperator(), c.getValue(), c.getValueTo(), c.getValueType()));
        return copy;
    }

    /**
     * Whether an application of this product is checked by this stop factor:
     * <ul>
     *     <li>limited to products: only those products;</li>
     *     <li>not limited: every product of its segment, or every product when it has no segment;</li>
     *     <li>a scoring without product (productId null) ignores product limits and is checked by segment.</li>
     * </ul>
     */
    public boolean appliesTo(Long productId, ProductSegment productSegment) {
        if (productId != null && !productIds.isEmpty()) {
            return productIds.contains(productId);
        }
        return segment == null || segment == productSegment;
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    public StopFactorCondition addCondition(StopFactorStatus result, ConditionOperator operator, String value, String valueTo,
                                            StopFactorValueTypes valueType) {
        StopFactorCondition condition = new StopFactorCondition(this, result, operator, value, valueTo, valueType);
        conditions.add(condition);
        return condition;
    }

    /** Removes every condition, to set the conditions of a new version anew. */
    public void removeConditions() {
        conditions.clear();
    }
}
