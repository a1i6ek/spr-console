package kg.bakai.stopfactor.calculator;

import kg.bakai.spr.core.kib.dto.OverdueForStopFactor;
import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.exception.InvalidParamsException;
import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.LegacyCounters;
import kg.bakai.stopfactor.report.LegacyOverdueCounters;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** No raw history: the SPR counters are the only source. */
    private static CheckContext legacy(OverdueForStopFactor overdue) {
        return context(overdue, null);
    }

    private static CheckContext raw(List<CreditHistoryEvents.OverdueMonth> overdueMonths) {
        return context(null, CreditHistoryEvents.ofOverdueMonths(overdueMonths));
    }

    /** @param history null - the overdue months cannot be found */
    private static CheckContext context(OverdueForStopFactor overdue, CreditHistoryEvents history) {
        CheckContext.Builder context = CheckContext.builder(1L)
                .fact(Facts.LEGACY_COUNTERS, new LegacyCounters(0, 0, 0, 0, 0, 0, LegacyOverdueCounters.from(overdue)));
        return (history != null ? context.fact(Facts.CREDIT_HISTORY, history) : context.noFact(Facts.CREDIT_HISTORY)).build();
    }

    @Test
    void seededBucketReadsItsLegacyField() {
        OverdueForStopFactor overdue = new OverdueForStopFactor(9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3);

        assertThat(calculator.calculate(parsed(1, 3, 1, 15), legacy(overdue))).contains(CheckedValue.ofNumber(9));
        // the KIB module has no counter for 1-10 days in months 1..3
        assertThat(calculator.calculate(parsed(1, 3, 1, 10), legacy(overdue))).isEqualTo(Optional.empty());
        assertThat(calculator.calculate(parsed(25, 36, 91, null), legacy(overdue))).contains(CheckedValue.ofNumber(3));
    }

    @Test
    void bucketWithoutLegacyFieldHasNoDataWithoutRawHistory() {
        assertThat(calculator.calculate(parsed(1, 6, 30, null), legacy(null))).isEqualTo(Optional.empty());
    }

    @Test
    void rawOverdueMonthsAreCountedInsidePeriodAndOverdueRangeInclusive() {
        List<CreditHistoryEvents.OverdueMonth> overdueMonths = List.of(
                new CreditHistoryEvents.OverdueMonth(1, 1),
                new CreditHistoryEvents.OverdueMonth(3, 10),
                new CreditHistoryEvents.OverdueMonth(4, 5),
                new CreditHistoryEvents.OverdueMonth(2, 11));

        assertThat(calculator.calculate(parsed(1, 3, 1, 10), raw(overdueMonths))).contains(CheckedValue.ofNumber(2));
        assertThat(calculator.calculate(parsed(1, 3, 11, null), raw(overdueMonths))).contains(CheckedValue.ofNumber(1));
    }

    @Test
    void rawHistoryTakesPrecedenceOverLegacyValues() {
        OverdueForStopFactor overdue = new OverdueForStopFactor(9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        CheckContext request = context(overdue, CreditHistoryEvents.ofOverdueMonths(List.of()));

        assertThat(calculator.calculate(parsed(1, 3, 1, 10), request)).contains(CheckedValue.ofNumber(0));
    }

    @Test
    void periodLongerThanTheCompleteHistoryUsesTheLegacyCounter() {
        // the calendars cover 24 months: no overdue months are known in months 25..36
        OverdueForStopFactor overdue = new OverdueForStopFactor(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4);
        CheckContext request = context(overdue,
                new CreditHistoryEvents(List.of(new CreditHistoryEvents.OverdueMonth(30, 120)), 24, null, null, null));

        assertThat(calculator.calculate(parsed(1, 24, 91, null), request)).contains(CheckedValue.ofNumber(0));
        assertThat(calculator.calculate(parsed(25, 36, 91, null), request)).contains(CheckedValue.ofNumber(4));
        assertThat(calculator.calculate(parsed(25, 36, 31, 60), request)).isEqualTo(Optional.empty());
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
