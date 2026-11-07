package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.LegacyCounters;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Count of events (bureau requests, new credits) in the last N days. Without the raw event dates: the legacy
 * counter, which has one period only (7 days of bureau requests, 30 days of new credits).
 */
public class RecentEventsCountCalculator extends RecordParamsCalculator<RecentEventsCountCalculator.Period> {

    public record Period(@Param(label = "Последние N дней", min = 1) int days) {
    }

    private final CalculatorType type;
    private final Function<CreditHistoryEvents, List<Integer>> rawEventsDaysAgo;
    private final BiFunction<LegacyCounters, Integer, OptionalDouble> legacyCount;

    /**
     * @param rawEventsDaysAgo the raw events of the history: days between each event and the request
     * @param legacyCount      the legacy counter for a number of days, empty for other periods
     */
    public RecentEventsCountCalculator(CalculatorType type,
                                       Function<CreditHistoryEvents, List<Integer>> rawEventsDaysAgo,
                                       BiFunction<LegacyCounters, Integer, OptionalDouble> legacyCount) {
        super(Period.class);
        this.type = type;
        this.rawEventsDaysAgo = rawEventsDaysAgo;
        this.legacyCount = legacyCount;
    }

    @Override
    public CalculatorType type() {
        return type;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.NUMBER;
    }

    @Override
    public Optional<CheckedValue> calculate(Period period, CheckContext context) {
        return EventCount.count(
                context.find(Facts.CREDIT_HISTORY).map(rawEventsDaysAgo).orElse(null),
                daysAgo -> daysAgo <= period.days(),
                () -> legacyCount.apply(context.get(Facts.LEGACY_COUNTERS), period.days()));
    }
}
