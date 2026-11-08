package kg.bakai.console.test;

import kg.bakai.spr.core.kib.dto.OverdueForStopFactor;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Application for the test check: the KIB and OB data SPR would pass to StopFactorService.check, in a short form.
 * Every field is optional; an omitted number is 0.
 *
 * @param scoringId   scoring the results are saved for; omitted - a new negative test id
 * @param productId   links the scoring to this product (public.scoring), so its segment and product limits apply;
 *                    omitted - the scoring keeps its product (scorings 1..5 of changelog 1.2), or has none
 * @param requestDate date of the application, today when omitted
 * @param kib         KIB report; null - the client has no KIB report
 * @param obCredits   credits of the OB report (Открытый банк)
 */
public record TestCheckRequest(
        Long scoringId,
        Long productId,
        LocalDate requestDate,
        @Valid Kib kib,
        List<@Valid ObCredit> obCredits
) {

    /**
     * @param overdueMonths       shortcut for the overdue history: each month becomes a main debtor contract with one
     *                            payment calendar entry, so KibOverdueHistoryReader returns exactly these months
     * @param contracts           contracts with raw payment calendars, read by KibOverdueHistoryReader as in SPR
     * @param calendarsUnavailable true imitates a report whose contracts have no payment calendars: no overdue months can
     *                            be found
     * @param legacyOverdue       the 17 legacy counters (KibService.getOverdueByPeriodAndCount), zeros when omitted
     * @param missingParts        parts of the KIB report SPR did not receive
     */
    public record Kib(
            int activeDisputes,
            int unjustifiedDisputes,
            int bureauRequestsLast7Days,
            BigDecimal overdueAmount,
            int newCreditsLast30Days,
            int openCredits,
            List<Loan> loans,
            List<OverdueMonth> overdueMonths,
            List<Contract> contracts,
            boolean calendarsUnavailable,
            OverdueForStopFactor legacyOverdue,
            Set<KibPart> missingParts
    ) {
    }

    /**
     * A KIB contract of the client with its monthly payment calendar.
     *
     * @param role      MainDebtor when omitted; only MainDebtor contracts count for the overdue history
     * @param startDate start of the contract; the first calendar month when omitted. Earlier than the calendar:
     *                  the history is not complete for the months before it
     * @param calendar  one entry per month
     */
    public record Contract(RoleOfClient role, LocalDate startDate, List<CalendarEntry> calendar) {
    }

    /**
     * One month of a KIB payment calendar, as KIB sends it.
     *
     * @param date           a day of the month (KIB sends its last day)
     * @param maxPastDueDays largest overdue days reached during the month, 0 - no overdue
     * @param pastDueDays    overdue days left at the end of the month: above 0 - the overdue goes on into the next
     *                       month, 0 - repaid within the month
     */
    public record CalendarEntry(LocalDate date, int maxPastDueDays, int pastDueDays) {
    }

    @Schema(description = "A part of the KIB report: without any of them the check treats the client as having no KIB report")
    public enum KibPart {
        CUSTOM_REPORT,
        INDIVIDUAL_REPORT,
        CONSOLIDATION
    }

    /**
     * @param role   MainDebtor when omitted
     * @param status Open when omitted
     */
    public record Loan(
            RoleOfClient role,
            LoanStatus status,
            int restructuringsLast12Months,
            int restructuringsFrom12To24Months,
            int daysDelay
    ) {
    }

    public record ObCredit(
            long overdueDays,
            BigDecimal overdueSum,
            LocalDate lastChangeDate
    ) {
    }
}
