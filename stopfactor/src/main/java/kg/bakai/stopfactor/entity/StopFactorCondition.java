package kg.bakai.stopfactor.entity;

import jakarta.persistence.*;
import kg.bakai.common.entity.BaseEntity;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A condition of a stop factor: REJECTED or UNDERWRITER when the measured value x fits the operator, at most one of
 * each per stop factor. Never changed once saved, like its stop factor: a change creates a new group version.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stop_factor_condition", schema = "stop_factors")
public class StopFactorCondition extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_condition_id_seq")
    @SequenceGenerator(name = "stop_factor_condition_id_seq", sequenceName = "stop_factor_condition_id_generator", schema = "public", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stop_factor_id", nullable = false, updatable = false)
    private StopFactor stopFactor;

    /** REJECTED or UNDERWRITER: what the stop factor decides when the condition holds. */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private StopFactorStatus result;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 16)
    private ConditionOperator operator;

    @Column(name = "value", nullable = false, length = Integer.MAX_VALUE)
    private String value;

    /** Upper bound of RANGE and BETWEEN, null for the other operators. */
    @Column(name = "value_to", length = Integer.MAX_VALUE)
    private String valueTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private StopFactorValueTypes valueType;

    StopFactorCondition(StopFactor stopFactor, StopFactorStatus result, ConditionOperator operator, String value,
                        String valueTo, StopFactorValueTypes valueType) {
        this.stopFactor = stopFactor;
        this.result = result;
        this.operator = operator;
        this.value = value;
        this.valueTo = valueTo;
        this.valueType = valueType;
    }
}
