package kg.bakai.stopfactor.entity;

import kg.bakai.common.entity.BaseEntity;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A condition of a stop factor: REJECTED or UNDERWRITER when the measured value fits the operator.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_condition", schema = "stop_factors")
public class StopFactorCondition extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_condition_id_seq")
    @SequenceGenerator(name = "stop_factor_condition_id_seq", sequenceName = "stop_factor_condition_id_generator", schema = "stop_factors", allocationSize = 1)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stop_factor_id", nullable = false, updatable = false)
    private StopFactor stopFactor;

    @NotNull
    @Column(name = "value", nullable = false, length = Integer.MAX_VALUE)
    private String value;

    @NotNull
    @Column(name = "value_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private StopFactorValueTypes valueType;

    @NotNull
    @Column(name = "operator", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private ConditionOperator operator;

    /** Upper bound (included) of a {@link ConditionOperator#RANGE} condition, null for the other operators. */
    @Column(name = "value_to", length = Integer.MAX_VALUE)
    private String valueTo;

    @Column(name = "result")
    @Enumerated(EnumType.STRING)
    private StopFactorStatus result;

    StopFactorCondition(StopFactor stopFactor, StopFactorStatus result, StopFactorValueTypes valueType, String value,
                        ConditionOperator operator, String valueTo) {
        this.stopFactor = stopFactor;
        this.result = result;
        this.valueType = valueType;
        this.value = value;
        this.operator = operator;
        this.valueTo = valueTo;
    }
}
