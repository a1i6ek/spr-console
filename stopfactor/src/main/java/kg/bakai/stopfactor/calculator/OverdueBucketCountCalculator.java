package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Count of months with overdue (main debtor contracts) in a period of months whose overdue is in a range of days
 * (both bounds included). Without overdue months, or when they do not cover the whole period: the legacy counter with
 * exactly these params, if there is one.
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
        return EventCount.count(
                context.find(Facts.CREDIT_HISTORY).map(history -> history.overdueMonthsUpTo(bucket.periodToMonth())).orElse(null),
                bucket::contains,
                () -> context.get(Facts.LEGACY_COUNTERS).overdueMonths().count(
                        bucket.periodFromMonth(), bucket.periodToMonth(), bucket.minOverdueDays(), bucket.maxOverdueDays()));
    }
}
