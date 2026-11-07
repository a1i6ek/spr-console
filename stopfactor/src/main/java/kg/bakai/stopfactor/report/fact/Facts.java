package kg.bakai.stopfactor.report.fact;

import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.LegacyCounters;
import kg.bakai.stopfactor.report.ReportValues;

import java.util.List;

/**
 * Every fact the calculators can ask for. Each one needs a {@link FactProvider}, otherwise the application does not
 * start ({@link CheckContextFactory}).
 */
public final class Facts {

    /** Values of the KIB / OB reports: disputes, current overdue, open credits. */
    public static final FactKey<ReportValues> REPORT_VALUES = FactKey.of("reportValues");

    /**
     * Fixed-period counters of the old algorithm, the fallback of the counting calculators. Asks the KIB module
     * (KibService.getOverdueByPeriodAndCount), so it is made only when a calculator needs it.
     */
    public static final FactKey<LegacyCounters> LEGACY_COUNTERS = FactKey.of("legacyCounters");

    /** Raw credit history events; empty when they cannot be found (no KIB payment calendars). */
    public static final FactKey<CreditHistoryEvents> CREDIT_HISTORY = FactKey.of("creditHistory");

    /** Statuses of the KIB loans of the client (main debtor or guarantor), in report order. */
    public static final FactKey<List<String>> LOAN_STATUSES = FactKey.of("loanStatuses");

    public static final List<FactKey<?>> ALL = List.of(REPORT_VALUES, LEGACY_COUNTERS, CREDIT_HISTORY, LOAN_STATUSES);

    private Facts() {
    }
}
