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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One rule of a group version: its metric with param values, its conditions (when the application is REJECTED or
 * goes to the UNDERWRITER) and which applications it checks (segment, products). Never changed once saved: a change
 * creates a new group version with new copies.
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

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private StopFactorGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_id", nullable = false)
    private StopFactorMetric metric;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    /** An inactive stop factor is not checked; the other stop factors of its group are. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Order inside the group. */
    @Column(name = "order_number", nullable = false)
    private int orderNumber;

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

    StopFactor(StopFactorGroup group, int orderNumber) {
        this.group = group;
        this.orderNumber = orderNumber;
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

    /** This stop factor's rule in another stop factor (of a new group version). */
    void copyTo(StopFactor copy) {
        copy.metric = metric;
        copy.name = name;
        copy.description = description;
        copy.active = active;
        copy.segment = segment;
        copy.params = new LinkedHashMap<>(params);
        copy.productIds = new HashSet<>(productIds);
        conditions.forEach(c -> copy.addCondition(c.getResult(), c.getOperator(), c.getValue(), c.getValueTo(), c.getValueType()));
    }
}
