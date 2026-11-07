package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.CreditHistoryEvents.RestructuringEvent;
import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Count of restructurings in KIB, or changes of credits in OB, in a period of months. Without raw events: the legacy
 * counter of months 1..12 or 13..24.
 */
@Component
public class RestructuringCountCalculator extends RecordParamsCalculator<RestructuringCountCalculator.Window> {

    public record Window(
            @Param(label = "Источник (KIB или OB)") CreditHistoryEvents.Source source,
            @Param(label = "Период с (месяц)", min = 1, notAbove = "periodToMonth") int periodFromMonth,
            @Param(label = "Период по (месяц)", min = 1) int periodToMonth
    ) {

        boolean contains(RestructuringEvent event) {
            return event.source() == source && event.monthsAgo() >= periodFromMonth && event.monthsAgo() <= periodToMonth;
        }
    }

    public RestructuringCountCalculator() {
        super(Window.class);
    }

    @Override
    public CalculatorType type() {
        return CalculatorType.RESTRUCTURING_COUNT;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.NUMBER;
    }

    @Override
    public Optional<CheckedValue> calculate(Window window, CheckContext context) {
        return EventCount.count(
                context.find(Facts.CREDIT_HISTORY).map(CreditHistoryEvents::restructurings).orElse(null),
                window::contains,
                () -> context.get(Facts.LEGACY_COUNTERS)
                        .restructurings(window.source(), window.periodFromMonth(), window.periodToMonth()));
    }
}
