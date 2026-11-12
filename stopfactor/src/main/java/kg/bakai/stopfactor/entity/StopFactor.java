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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A stop factor of a group version: the param values its metric is measured with and the conditions that judge the
 * value, e.g. "с 7 по 12 месяц, 6..10 дней: жёлтая зона 6..8, отказ больше 8", and the segment and products it
 * applies to. Every stop factor is checked on its own; a change of any of them closes the group and creates a new
 * group version with all of them copied.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stop_factor", schema = "stop_factors")
public class StopFactor extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_id_seq")
    @SequenceGenerator(name = "stop_factor_id_seq", sequenceName = "stop_factor_id_generator",
            schema = "public", allocationSize = 1)
    @Setter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private StopFactorGroup group;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_id", nullable = false)
    private StopFactorMetric metric;

    /** The metric's name template rendered with the params. */
    @Setter
    @Column(name = "name", nullable = false)
    private String name;

    /** An inactive stop factor is not checked; the group checks its other stop factors. */
    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Order inside the group, from 1. */
    @Column(name = "order_number", nullable = false, updatable = false)
    private int orderNumber;

    /** Values of the metric params, e.g. {"periodFromMonth": 7, "periodToMonth": 12}. */
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> params = new LinkedHashMap<>();

    /** Segment of the products it applies to, null - every segment. */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "segment", length = 32)
    private ProductSegment segment;

    /** Products it is limited to, empty - every product. */
    @Setter
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "stop_factor_product",
            schema = "stop_factors",
            joinColumns = @JoinColumn(name = "stop_factor_id"))
    @Column(name = "product_id", nullable = false)
    private Set<Long> productIds = new HashSet<>();

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "stopFactor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StopFactorCondition> conditions = new ArrayList<>();

    StopFactor(StopFactorGroup group, int orderNumber) {
        this.group = group;
        this.orderNumber = orderNumber;
    }

    /** Param values by key as a calculator reads them (Integer or String), in the order of the metric params. */
    public Map<String, Object> getParams() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        metric.getParams().stream()
                .map(MetricParamSchema::key)
                .filter(params::containsKey)
                .forEach(key -> ordered.put(key, params.get(key)));
        params.forEach(ordered::putIfAbsent);
        return ordered;
    }

    /** Sets the param values; every key must be a param of the metric, null values are left out. */
    public void setParams(Map<String, ?> params) {
        Map<String, Object> values = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (value != null) {
                MetricParamSchema definition = metric.findParam(key).orElseThrow(() -> new IllegalArgumentException(
                        "Параметр «" + key + "» не задан в метрике «" + metric.getTitle() + "»."));
                values.put(definition.key(), value);
            }
        });
        this.params = values;
    }

    /**
     * Whether an application of this product is checked by this stop factor:
     * <ul>
     *     <li>limited to products: only those products, whatever their segment is now - SPR may move a product to
     *     another segment, a product chosen in the console stays checked;</li>
     *     <li>not limited: every product of its segment, or every product without a segment;</li>
     *     <li>a scoring without product (productId null) ignores product limits and is checked by segment.</li>
     * </ul>
     */
    public boolean appliesTo(Long productId, ProductSegment productSegment) {
        if (productId != null && !productIds.isEmpty()) {
            return productIds.contains(productId);
        }
        return segment == null || segment == productSegment;
    }

    public List<StopFactorCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public StopFactorCondition addCondition(StopFactorStatus result, StopFactorValueTypes valueType, String value,
                                            ConditionOperator operator, String valueTo) {
        StopFactorCondition condition = new StopFactorCondition(this, result, valueType, value, operator, valueTo);
        conditions.add(condition);
        return condition;
    }

    /** The same stop factor in another version of the group. */
    StopFactor copyTo(StopFactorGroup groupVersion) {
        StopFactor copy = new StopFactor(groupVersion, orderNumber);
        copy.metric = metric;
        copy.name = name;
        copy.active = active;
        copy.params = new LinkedHashMap<>(params);
        copy.segment = segment;
        copy.productIds = new HashSet<>(productIds);
        conditions.forEach(c -> copy.addCondition(c.getResult(), c.getValueType(), c.getValue(), c.getOperator(), c.getValueTo()));
        return copy;
    }
}
