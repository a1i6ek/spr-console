package kg.bakai.stopfactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

/**
 * A condition of a stop factor, at most one REJECTED and one UNDERWRITER per stop factor.
 *
 * @param operator  how the measured value x is compared: GT (x > value), GTE, LT, LTE, EQ, RANGE (value < x <= valueTo),
 *                  BETWEEN (value <= x <= valueTo) for numbers, IN (a loan has one of the statuses) for loan statuses
 * @param valueTo   upper bound of RANGE and BETWEEN, null for the other operators
 * @param valueType INTEGER, DOUBLE for numbers, ARRAY (statuses separated by commas), STRING (one status)
 */
public record ConditionDto(
        @NotNull StopFactorStatus result,
        @NotNull ConditionOperator operator,
        @NotBlank String value,
        String valueTo,
        @NotNull StopFactorValueTypes valueType
) {
}
