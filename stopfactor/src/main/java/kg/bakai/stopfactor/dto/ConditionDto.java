package kg.bakai.stopfactor.dto;

import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A condition of a stop factor: at most one REJECTED and one UNDERWRITER condition per stop factor.
 *
 * @param value    the threshold of a number condition (INTEGER, DOUBLE) or the loan statuses (ARRAY, STRING)
 * @param operator how x is compared: GT, GTE, LT, LTE, EQ, RANGE, BETWEEN for numbers, IN for loan statuses
 *                 (labels for the console select: GET /api/v1/admin/metrics/calculators, allowedOperators). Left out
 *                 it is IN for loan statuses and GT for numbers; an UNDERWRITER number left out next to a REJECTED GT
 *                 is RANGE up to the REJECTED value, as legacy thresholds meant
 * @param valueTo  upper bound (included) of RANGE ({@code value < x <= valueTo}) and BETWEEN
 *                 ({@code value <= x <= valueTo}); null for the other operators
 */
public record ConditionDto(
        Long id,
        @NotNull StopFactorStatus result,
        @NotNull StopFactorValueTypes valueType,
        @NotBlank String value,
        ConditionOperator operator,
        String valueTo
) {

    /** A legacy threshold, without operator and valueTo: the console sets the default operator. */
    public ConditionDto(Long id, StopFactorStatus result, StopFactorValueTypes valueType, String value) {
        this(id, result, valueType, value, null, null);
    }
}
