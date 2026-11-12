package kg.bakai.stopfactor.entity;

import kg.bakai.common.entity.BaseEntity;
import kg.bakai.stopfactor.enums.CalculatorType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a stop factor measures, e.g. "Непрерывная просроченная задолженность": the calculator, the params
 * ({@link MetricParamSchema}) and the name template. Its stop factors are the metric with param values.
 * <p>
 * Versioned like a group: a change of the definition (title, name template, params) closes the version and creates
 * a new one, and a stop factor keeps the version it was saved with. A check or a group version of a past date
 * therefore shows the metric as it was then. The calculator never changes; the active flag only decides whether new
 * stop factors may use the metric, so it is changed in place.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_metric", schema = "stop_factors")
public class StopFactorMetric extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_metric_id_seq")
    @SequenceGenerator(name = "stop_factor_metric_id_seq", sequenceName = "stop_factor_metric_id_generator", schema = "stop_factors", allocationSize = 1)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

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

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculator", nullable = false, updatable = false)
    private CalculatorType calculator;

    @Column(name = "name_template", nullable = false, length = Integer.MAX_VALUE)
    private String nameTemplate;

    /** What the rules of its stop factors are set up with, in console order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    private List<MetricParamSchema> params = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** The id of the metric: the id of its first version, the same for all its versions. */
    public Long getMetricId() {
        return rootId != null ? rootId : id;
    }

    public boolean isCurrent() {
        return validTo == null;
    }

    public Optional<MetricParamSchema> findParam(String key) {
        return params.stream().filter(param -> param.key().equals(key)).findFirst();
    }

    /** The next version, valid from now, with the same definition; the caller closes this one. */
    public StopFactorMetric newVersion(LocalDateTime now, String reason) {
        StopFactorMetric next = new StopFactorMetric();
        next.rootId = getMetricId();
        next.versionNumber = versionNumber + 1;
        next.validFrom = now;
        next.changeReason = reason;
        next.title = title;
        next.calculator = calculator;
        next.nameTemplate = nameTemplate;
        next.params = new ArrayList<>(params);
        next.active = active;
        return next;
    }
}
