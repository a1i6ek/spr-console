package kg.bakai.stopfactor.entity;

import jakarta.persistence.*;
import kg.bakai.common.entity.BaseEntity;
import kg.bakai.stopfactor.enums.CalculatorType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a stop factor measures, e.g. "Непрерывная просроченная задолженность": the calculator and the params a stop
 * factor sets for it. Edited in place; group versions keep their own param values, so history stays correct.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_metric", schema = "stop_factors")
public class StopFactorMetric extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_metric_id_seq")
    @SequenceGenerator(name = "stop_factor_metric_id_seq", sequenceName = "stop_factor_metric_id_generator", schema = "public", allocationSize = 1)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculator", nullable = false, length = 64)
    private CalculatorType calculator;

    /** The params a stop factor of this metric sets, in console order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    private List<MetricParam> params = new ArrayList<>();

    /** An inactive metric cannot be given to a new or changed stop factor. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Optional<MetricParam> findParam(String key) {
        return params.stream().filter(param -> param.key().equals(key)).findFirst();
    }
}
