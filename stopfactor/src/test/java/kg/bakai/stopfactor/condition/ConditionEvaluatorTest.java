package kg.bakai.stopfactor.condition;

import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.condition.ConditionEvaluator.Outcome;
import kg.bakai.stopfactor.entity.StopFactorCondition;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kg.bakai.stopfactor.enums.StopFactorStatus.*;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    @Test
    void underwriterRangeUpToTheRejectedValue() {
        // "6-8 - жёлтая зона, 9+ - отказ", as changelog 1.3 and the console store it
        List<StopFactorCondition> conditions = List.of(condition(REJECTED, INTEGER, "8"), range(UNDERWRITER, "5", "8"));

        assertThat(apply(5, conditions)).isEqualTo(new Outcome(APPROVED, "5.0"));
        assertThat(apply(6, conditions)).isEqualTo(new Outcome(UNDERWRITER, "6.0"));
        assertThat(apply(8, conditions)).isEqualTo(new Outcome(UNDERWRITER, "8.0"));
        assertThat(apply(9, conditions)).isEqualTo(new Outcome(REJECTED, "9.0"));
    }

    @Test
    void underwriterGreaterThanGivesTheSameResultsAsTheRange() {
        // legacy conditions (before changelog 1.3): REJECTED is checked first, so UNDERWRITER GT never fired above 8
        List<StopFactorCondition> greaterThan = List.of(condition(REJECTED, INTEGER, "8"), condition(UNDERWRITER, INTEGER, "5"));
        List<StopFactorCondition> range = List.of(condition(REJECTED, INTEGER, "8"), range(UNDERWRITER, "5", "8"));

        for (int value = 0; value <= 12; value++) {
            assertThat(apply(value, range)).isEqualTo(apply(value, greaterThan));
        }
    }

    @Test
    void rangeIncludesItsUpperBoundOnly() {
        List<StopFactorCondition> conditions = List.of(range(UNDERWRITER, "1.5", "2.5"));

        assertThat(apply(1.5, conditions).result()).isEqualTo(APPROVED);
        assertThat(apply(1.51, conditions).result()).isEqualTo(UNDERWRITER);
        assertThat(apply(2.5, conditions).result()).isEqualTo(UNDERWRITER);
        assertThat(apply(2.51, conditions).result()).isEqualTo(APPROVED);
    }

    @Test
    void everyNumberOperatorAtItsBounds() {
        // value -> fires, for value 5 (and valueTo 8)
        assertFires(operator(ConditionOperator.GT, "5", null), Map.of(4.0, false, 5.0, false, 5.5, true, 100.0, true));
        assertFires(operator(ConditionOperator.GTE, "5", null), Map.of(4.0, false, 4.99, false, 5.0, true, 100.0, true));
        assertFires(operator(ConditionOperator.LT, "5", null), Map.of(0.0, true, 4.99, true, 5.0, false, 6.0, false));
        assertFires(operator(ConditionOperator.LTE, "5", null), Map.of(0.0, true, 5.0, true, 5.01, false, 6.0, false));
        assertFires(operator(ConditionOperator.EQ, "5", null), Map.of(4.0, false, 5.0, true, 5.01, false));
        assertFires(operator(ConditionOperator.RANGE, "5", "8"), Map.of(5.0, false, 5.01, true, 8.0, true, 8.01, false));
        assertFires(operator(ConditionOperator.BETWEEN, "5", "8"), Map.of(4.99, false, 5.0, true, 8.0, true, 8.01, false));
    }

    @Test
    void rejectedIsCheckedBeforeUnderwriterWithAnyOperators() {
        // 7-8 - жёлтая зона, 9+ - отказ
        List<StopFactorCondition> conditions = List.of(
                operator(ConditionOperator.GTE, "9", null), withResult(operator(ConditionOperator.BETWEEN, "7", "8"), UNDERWRITER));

        assertThat(apply(6, conditions).result()).isEqualTo(APPROVED);
        assertThat(apply(7, conditions).result()).isEqualTo(UNDERWRITER);
        assertThat(apply(8, conditions).result()).isEqualTo(UNDERWRITER);
        assertThat(apply(9, conditions).result()).isEqualTo(REJECTED);
    }

    @Test
    void underwriterAloneFiresAboveItsValue() {
        assertThat(apply(0, List.of(condition(UNDERWRITER, INTEGER, "0"))).result()).isEqualTo(APPROVED);
        assertThat(apply(100, List.of(condition(UNDERWRITER, INTEGER, "0"))).result()).isEqualTo(UNDERWRITER);
    }

    @Test
    void doubleCondition() {
        assertThat(apply(1500.75, List.of(condition(REJECTED, DOUBLE, "1500.5"))).result()).isEqualTo(REJECTED);
        assertThat(apply(1500.5, List.of(condition(REJECTED, DOUBLE, "1500.5"))).result()).isEqualTo(APPROVED);
    }

    @Test
    void noConditionsApproveWithTheValue() {
        assertThat(apply(3, List.of())).isEqualTo(new Outcome(APPROVED, "3.0"));
    }

    @Test
    void statusConditionShowsTheFirstLoanStatusFound() {
        List<StopFactorCondition> conditions = List.of(
                condition(REJECTED, ARRAY, "WrittenOff, SoldToThirdParty"),
                condition(UNDERWRITER, ARRAY, "TransferredToOtherReportingInst"));

        assertThat(statuses(conditions, "Open", "SoldToThirdParty", "WrittenOff")).isEqualTo(new Outcome(REJECTED, "SoldToThirdParty"));
        assertThat(statuses(conditions, "TransferredToOtherReportingInst")).isEqualTo(new Outcome(UNDERWRITER, "TransferredToOtherReportingInst"));
        assertThat(statuses(conditions, "Open")).isEqualTo(new Outcome(APPROVED, ConditionEvaluator.NO_MATCHES));
    }

    @Test
    void statusConditionWithoutSpacesUnknownStatusesAndStringType() {
        assertThat(statuses(List.of(condition(REJECTED, ARRAY, "NoSuchStatus,WrittenOff")), "WrittenOff").result()).isEqualTo(REJECTED);
        assertThat(statuses(List.of(condition(REJECTED, ARRAY, "writtenoff")), "WrittenOff").result()).isEqualTo(APPROVED);
        assertThat(statuses(List.of(condition(REJECTED, STRING, "WrittenOff")), "WrittenOff").result()).isEqualTo(REJECTED);
    }

    @Test
    void numbersSavedWithSpacesAreRead() {
        List<StopFactorCondition> conditions = List.of(condition(REJECTED, INTEGER, " 8 "), range(UNDERWRITER, " 5", "8 "));

        assertThat(apply(6, conditions)).isEqualTo(new Outcome(UNDERWRITER, "6.0"));
        assertThat(apply(9, conditions)).isEqualTo(new Outcome(REJECTED, "9.0"));
    }

    private static Outcome apply(double value, List<StopFactorCondition> conditions) {
        return ConditionEvaluator.apply(CheckedValue.ofNumber(value), conditions);
    }

    private static Outcome statuses(List<StopFactorCondition> conditions, String... loanStatuses) {
        return ConditionEvaluator.apply(CheckedValue.ofValues(List.of(loanStatuses)), conditions);
    }

    /** GT for numbers, IN for loan statuses. */
    private static StopFactorCondition condition(StopFactorStatus result, StopFactorValueTypes type, String value) {
        StopFactorCondition condition = new StopFactorCondition();
        condition.setResult(result);
        condition.setValueType(type);
        condition.setValue(value);
        condition.setOperator(type == ARRAY || type == STRING ? ConditionOperator.IN : ConditionOperator.GT);
        return condition;
    }

    private static void assertFires(StopFactorCondition condition, Map<Double, Boolean> expected) {
        expected.forEach((value, fires) -> assertThat(apply(value, List.of(condition)).result())
                .as("%s %s %s", value, condition.getOperator().getName(), condition.getValue())
                .isEqualTo(fires ? condition.getResult() : APPROVED));
    }

    /** A REJECTED number condition with the operator. */
    private static StopFactorCondition operator(ConditionOperator operator, String value, String valueTo) {
        StopFactorCondition condition = condition(REJECTED, INTEGER, value);
        condition.setOperator(operator);
        condition.setValueTo(valueTo);
        return condition;
    }

    private static StopFactorCondition withResult(StopFactorCondition condition, StopFactorStatus result) {
        condition.setResult(result);
        return condition;
    }

    private static StopFactorCondition range(StopFactorStatus result, String value, String valueTo) {
        StopFactorCondition condition = condition(result, DOUBLE, value);
        condition.setOperator(ConditionOperator.RANGE);
        condition.setValueTo(valueTo);
        return condition;
    }
}
