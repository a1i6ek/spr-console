package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Count of months with overdue (main debtor contracts) in a period of months whose overdue is in a range of days
 * (both bounds included). Always a number: a month the KIB payment calendars do not show had no overdue, so a client
 * without calendars, or with a calendar shorter than the period, gets 0 for those months and never NO_DATA
 * (analyst's decision of 09.11.2026).
 */
@Component
public class OverdueBucketCountCalculator extends RecordParamsCalculator<OverdueBucketCountCalculator.Bucket> {

    /**
     * @param maxOverdueDays null - no upper bound
     */
    public record Bucket(
            @Param(label = "Период с (месяц)", min = 1, notAbove = "periodToMonth") int periodFromMonth,
            @Param(label = "Период по (месяц)", min = 1) int periodToMonth,
            @Param(label = "Максимальная просрочка от (дней)", min = 1, notAbove = "maxOverdueDays") int minOverdueDays,
            @Param(label = "Максимальная просрочка до (дней)", min = 1) Integer maxOverdueDays
    ) {

        boolean contains(OverdueMonth month) {
            return month.monthsAgo() >= periodFromMonth && month.monthsAgo() <= periodToMonth
                    && month.maxOverdueDays() >= minOverdueDays
                    && (maxOverdueDays == null || month.maxOverdueDays() <= maxOverdueDays);
        }
    }

    public OverdueBucketCountCalculator() {
        super(Bucket.class);
    }

    @Override
    public CalculatorType type() {
        return CalculatorType.OVERDUE_BUCKET_COUNT;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.NUMBER;
    }

    @Override
    public Optional<CheckedValue> calculate(Bucket bucket, CheckContext context) {
        List<OverdueMonth> months = context.find(Facts.CREDIT_HISTORY)
                .map(CreditHistoryEvents::overdueMonths)
                .orElse(List.of());
        return Optional.of(CheckedValue.ofNumber(months.stream().filter(bucket::contains).count()));
    }
}
