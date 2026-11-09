package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.exception.InvalidParamsException;
import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverdueBucketCountCalculatorTest {

    private final OverdueBucketCountCalculator calculator = new OverdueBucketCountCalculator();

    private static Map<String, Object> bucket(int from, int to, int min, Integer max) {
        Map<String, Object> params = new HashMap<>(Map.of("periodFromMonth", from, "periodToMonth", to, "minOverdueDays", min));
        if (max != null) {
            params.put("maxOverdueDays", max);
        }
        return params;
    }

    /** Params as stored, parsed the way the check parses them. */
    private OverdueBucketCountCalculator.Bucket parsed(int from, int to, int min, Integer max) {
        return calculator.parseParams(bucket(from, to, min, max));
    }

    private static CheckContext history(List<CreditHistoryEvents.OverdueMonth> overdueMonths) {
        return CheckContext.builder(1L).fact(Facts.CREDIT_HISTORY, CreditHistoryEvents.ofOverdueMonths(overdueMonths)).build();
    }

    @Test
    void overdueMonthsAreCountedInsidePeriodAndOverdueRangeInclusive() {
        List<CreditHistoryEvents.OverdueMonth> overdueMonths = List.of(
                new CreditHistoryEvents.OverdueMonth(1, 1),
                new CreditHistoryEvents.OverdueMonth(3, 10),
                new CreditHistoryEvents.OverdueMonth(4, 5),
                new CreditHistoryEvents.OverdueMonth(2, 11));

        assertThat(calculator.calculate(parsed(1, 3, 1, 10), history(overdueMonths))).contains(CheckedValue.ofNumber(2));
        assertThat(calculator.calculate(parsed(1, 3, 11, null), history(overdueMonths))).contains(CheckedValue.ofNumber(1));
        assertThat(calculator.calculate(parsed(4, 6, 1, null), history(overdueMonths))).contains(CheckedValue.ofNumber(1));
    }

    /** Any period and range is a number: the 2026 bands have no legacy counter and still never give NO_DATA. */
    @Test
    void everyBucketIsANumberWithoutOverdue() {
        assertThat(calculator.calculate(parsed(1, 3, 1, 10), history(List.of()))).contains(CheckedValue.ofNumber(0));
        assertThat(calculator.calculate(parsed(25, 36, 31, 60), history(List.of()))).contains(CheckedValue.ofNumber(0));
        assertThat(calculator.calculate(parsed(25, 36, 61, null), history(List.of()))).contains(CheckedValue.ofNumber(0));
    }

    @Test
    void monthsTheCalendarsDoNotShowCountAsWithoutOverdue() {
        // only months 1..24 are known: months 25..36 had no overdue
        List<CreditHistoryEvents.OverdueMonth> overdueMonths = List.of(new CreditHistoryEvents.OverdueMonth(5, 25));

        assertThat(calculator.calculate(parsed(25, 36, 31, 60), history(overdueMonths))).contains(CheckedValue.ofNumber(0));
        assertThat(calculator.calculate(parsed(4, 6, 21, null), history(overdueMonths))).contains(CheckedValue.ofNumber(1));
    }

    @Test
    void withoutACreditHistoryFactTheCountIsZero() {
        CheckContext noHistory = CheckContext.builder(1L).noFact(Facts.CREDIT_HISTORY).build();

        assertThat(calculator.calculate(parsed(1, 3, 1, 15), noHistory)).contains(CheckedValue.ofNumber(0));
    }

    @Test
    void paramsAreNormalizedAndValidated() {
        assertThat(calculator.normalizeParams(Map.of("periodFromMonth", "1", "periodToMonth", 3.0, "minOverdueDays", 1L)))
                .isEqualTo(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1));

        assertThatThrownBy(() -> calculator.normalizeParams(Map.of("periodFromMonth", 6, "periodToMonth", 3, "minOverdueDays", 1)))
                .isInstanceOf(InvalidParamsException.class)
                .hasMessageContaining("не может быть больше «Период по (месяц)»");
        assertThatThrownBy(() -> calculator.normalizeParams(Map.of("periodFromMonth", 1.5, "periodToMonth", 3, "foo", 1)))
                .isInstanceOf(InvalidParamsException.class)
                .hasMessageContaining("Параметр «foo» не используется этой метрикой")
                .hasMessageContaining("«Период с (месяц)» должно быть целым числом")
                .hasMessageContaining("Укажите «Максимальная просрочка от (дней)»");
    }
}
