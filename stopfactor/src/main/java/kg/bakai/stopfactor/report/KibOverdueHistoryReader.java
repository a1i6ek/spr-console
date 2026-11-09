package kg.bakai.stopfactor.report;

import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.CalendarItem;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract;
import kg.bakai.spr.core.kib.dto.response.Contracts;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static java.time.temporal.ChronoUnit.MONTHS;

/**
 * The months with overdue of a client, from the payment calendars of the KIB custom report: the source of every
 * OVERDUE_BUCKET_COUNT stop factor (any period and overdue days range).
 * <p>
 * Counts as the KIB module does for the legacy counters (KibOverdueCalculator):
 * <ul>
 *     <li>only contracts where the client is the <b>main debtor</b> (not guarantor, not co-debtor);</li>
 *     <li>every calendar month with {@code MaxPastDueDays} of 1 and more is one overdue month with that many days -
 *     an overdue that goes on into the next month counts in both months;</li>
 *     <li>month 1 is the month of the request, 2 - the month before, and so on; months after it are ignored.</li>
 * </ul>
 * A month the payment calendars do not show had no overdue (analyst's decision of 09.11.2026, as the KIB module):
 * a contract without a calendar is skipped, and a calendar shorter than its contract (KIB sends 12 or 24 months)
 * shows no overdue in the months before it.
 */
@Slf4j
@Component
public class KibOverdueHistoryReader {

    /**
     * The overdue months of a client.
     *
     * @param months months with overdue of all the main debtor contracts
     */
    public record OverdueHistory(List<OverdueMonth> months) {

        public static final OverdueHistory NONE = new OverdueHistory(List.of());
    }

    /**
     * @return the history; empty (no overdue) without main debtor contracts or without payment calendars
     */
    public OverdueHistory read(CustomReport customReport, LocalDateTime requestDate) {
        List<Contract> contracts = mainDebtorContracts(customReport);
        if (contracts.isEmpty()) {
            return OverdueHistory.NONE;
        }
        List<Contract> withCalendar = contracts.stream().filter(contract -> !calendarItems(contract).isEmpty()).toList();
        if (withCalendar.isEmpty()) {
            log.warn("[#read] Нет платежных календарей КИБ по {} договорам: месяцы просрочки не найдены, просрочки "
                    + "считаются отсутствующими", contracts.size());
            return OverdueHistory.NONE;
        }
        if (withCalendar.size() < contracts.size()) {
            log.warn("[#read] Нет платежного календаря КИБ у {} из {} договоров, их просрочки не учтены",
                    contracts.size() - withCalendar.size(), contracts.size());
        }

        YearMonth requestMonth = YearMonth.from(requestDate);
        List<OverdueMonth> months = new ArrayList<>();
        for (Contract contract : withCalendar) {
            for (CalendarItem item : calendarUpTo(contract, requestMonth)) {
                int days = maxPastDueDays(item);
                if (days > 0) {
                    months.add(new OverdueMonth(monthsAgo(YearMonth.from(item.date()), requestMonth), days));
                }
            }
        }
        log.debug("[#read] Месяцев с просрочкой: {}", months.size());
        return new OverdueHistory(months);
    }

    /** Calendar entries with a date up to the month of the request, in date order. */
    private static List<CalendarItem> calendarUpTo(Contract contract, YearMonth requestMonth) {
        return calendarItems(contract).stream()
                .filter(item -> item.date() != null && !YearMonth.from(item.date()).isAfter(requestMonth))
                .sorted(Comparator.comparing(CalendarItem::date))
                .toList();
    }

    /** 1 - the month of the request, 2 - the month before it, and so on (as KibOverdueCalculator). */
    static int monthsAgo(YearMonth month, YearMonth requestMonth) {
        return (int) MONTHS.between(month, requestMonth) + 1;
    }

    /** Largest overdue of the month; an entry without MaxPastDueDays has at least its overdue at the end. */
    private static int maxPastDueDays(CalendarItem item) {
        return Math.max(Objects.requireNonNullElse(item.maxPastDueDays(), 0), Objects.requireNonNullElse(item.pastDueDays(), 0));
    }

    private static List<Contract> mainDebtorContracts(CustomReport customReport) {
        if (customReport == null) {
            return List.of();
        }
        Contracts contracts = customReport.contracts();
        if (contracts == null || contracts.contractList() == null || contracts.contractList().contracts() == null) {
            return List.of();
        }
        return contracts.contractList().contracts().stream()
                .filter(Objects::nonNull)
                .filter(contract -> contract.roleOfClient() == RoleOfClient.MainDebtor)
                .toList();
    }

    private static List<CalendarItem> calendarItems(Contract contract) {
        if (contract.paymentCalendarList() == null || contract.paymentCalendarList().paymentCalendarItems() == null) {
            return List.of();
        }
        return contract.paymentCalendarList().paymentCalendarItems().stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
