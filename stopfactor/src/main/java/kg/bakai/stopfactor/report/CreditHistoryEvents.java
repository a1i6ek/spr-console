package kg.bakai.stopfactor.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Raw credit history events. A counting calculator uses a list when it is not null; the restructurings, bureau
 * requests and new credits fall back to {@link LegacyCounters} when their list is null.
 *
 * @param overdueMonths months with overdue of the main debtor contracts; empty without a KIB report or without
 *                      payment calendars (no overdue)
 */
public record CreditHistoryEvents(
        List<@NotNull @Valid OverdueMonth> overdueMonths,
        List<@NotNull @Valid RestructuringEvent> restructurings,
        List<@NotNull @Min(0) Integer> bureauRequestsDaysAgo,
        List<@NotNull @Min(0) Integer> newCreditsDaysAgo
) {

    /** Only overdue months. */
    public static CreditHistoryEvents ofOverdueMonths(List<OverdueMonth> overdueMonths) {
        return new CreditHistoryEvents(overdueMonths, null, null, null);
    }

    /**
     * One payment calendar month of a main debtor contract with overdue (KibOverdueHistoryReader). An overdue
     * that goes on into the next month is a month with overdue in both months, as the KIB module counts.
     *
     * @param monthsAgo      the calendar month: 1 - the month of the request, 2 - the month before it, and so on
     * @param maxOverdueDays largest overdue days reached in the month (MaxPastDueDays)
     */
    public record OverdueMonth(
            @Min(1) int monthsAgo,
            @Min(1) int maxOverdueDays
    ) {
    }

    public record RestructuringEvent(
            @NotNull Source source,
            @Min(1) int monthsAgo
    ) {
    }

    public enum Source {
        KIB,
        OB
    }
}
