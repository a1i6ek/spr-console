package kg.bakai.stopfactor.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How a threshold compares the measured value x with its {@code value} and {@code value_to}.
 * The constant is what the API sends and the database stores; {@link #getName()} is the label for the console
 * select. Keep in sync with ck_stop_factor_rejected and ck_stop_factor_underwriter (changelog 1.3).
 */
@Getter
@RequiredArgsConstructor
public enum ConditionOperator {
    /** {@code x > value}. */
    GT(">"),
    /** {@code x >= value}. */
    GTE(">="),
    /** {@code x < value}. */
    LT("<"),
    /** {@code x <= value}. */
    LTE("<="),
    /** {@code x = value}. */
    EQ("="),
    /** {@code value < x <= value_to}: "6-8 - жёлтая зона" next to "отказ больше 8" is RANGE 5..8. */
    RANGE("> и <="),
    /** {@code value <= x <= value_to}: "6-8 - жёлтая зона" is BETWEEN 6..8. */
    BETWEEN(">= и <="),
    /** Loan statuses (ARRAY, STRING): a loan of the client has one of the statuses in value. */
    IN("in");

    private final String name;

    /** The condition as a formula of x, the measured value: "x > 8", "5 < x ≤ 8", "статус ∈ Closed, Sold". */
    public String formula(String value, String valueTo) {
        return switch (this) {
            case GT -> "x > " + value;
            case GTE -> "x ≥ " + value;
            case LT -> "x < " + value;
            case LTE -> "x ≤ " + value;
            case EQ -> "x = " + value;
            case RANGE -> value + " < x ≤ " + valueTo;
            case BETWEEN -> value + " ≤ x ≤ " + valueTo;
            case IN -> "статус ∈ " + value;
        };
    }

    /** RANGE and BETWEEN have an upper bound in value_to; the other operators have none. */
    public boolean hasValueTo() {
        return this == RANGE || this == BETWEEN;
    }

    /** Whether a number x fires a number condition; valueTo is used only by RANGE and BETWEEN. */
    public boolean test(double x, double value, double valueTo) {
        return switch (this) {
            case GT -> x > value;
            case GTE -> x >= value;
            case LT -> x < value;
            case LTE -> x <= value;
            case EQ -> x == value;
            case RANGE -> x > value && x <= valueTo;
            case BETWEEN -> x >= value && x <= valueTo;
            case IN -> throw new IllegalStateException("IN compares loan statuses, not numbers");
        };
    }
}
