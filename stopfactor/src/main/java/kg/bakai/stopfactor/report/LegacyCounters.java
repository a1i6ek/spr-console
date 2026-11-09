package kg.bakai.stopfactor.report;

import java.util.OptionalDouble;

/**
 * Every counter of the old stop factor algorithm in one place. Each one was calculated for a fixed period, so it
 * answers exactly one set of stop factor params: a counting calculator reads it only when SPR gives no raw events
 * ({@link CreditHistoryEvents}), and for any other params there is no data (NO_DATA, underwriter). The overdue stop
 * factors do not use them: they count the months of the KIB payment calendars.
 *
 * @param kibRestructurings1To12Months  restructurings of the KIB loans of the client, months 1..12
 * @param kibRestructurings13To24Months restructurings of the KIB loans of the client, months 13..24
 * @param obChanges1To12Months          OB credits changed in months 1..12
 * @param obChanges13To24Months         OB credits changed in months 13..24
 * @param bureauRequestsLast7Days       credit bureau requests in the last 7 days
 * @param newCreditsLast30Days          credits opened in the last 30 days
 */
public record LegacyCounters(
        double kibRestructurings1To12Months,
        double kibRestructurings13To24Months,
        double obChanges1To12Months,
        double obChanges13To24Months,
        double bureauRequestsLast7Days,
        double newCreditsLast30Days
) {

    /** A client without a credit history in KIB and without changed OB credits: every counter is 0. */
    public static final LegacyCounters NONE = new LegacyCounters(0, 0, 0, 0, 0, 0);

    /**
     * A client without a KIB report: every KIB counter is 0, the OB counters come from the OB report, which is
     * always there.
     *
     * @param obChanges1To12Months  OB credits changed in months 1..12
     * @param obChanges13To24Months OB credits changed in months 13..24
     */
    public static LegacyCounters obOnly(double obChanges1To12Months, double obChanges13To24Months) {
        return new LegacyCounters(0, 0, obChanges1To12Months, obChanges13To24Months, 0, 0);
    }

    /** Restructurings (KIB) or changed credits (OB) in months periodFrom..periodTo: 1..12 and 13..24 only. */
    public OptionalDouble restructurings(CreditHistoryEvents.Source source, int periodFromMonth, int periodToMonth) {
        boolean kib = source == CreditHistoryEvents.Source.KIB;
        if (periodFromMonth == 1 && periodToMonth == 12) {
            return OptionalDouble.of(kib ? kibRestructurings1To12Months : obChanges1To12Months);
        }
        if (periodFromMonth == 13 && periodToMonth == 24) {
            return OptionalDouble.of(kib ? kibRestructurings13To24Months : obChanges13To24Months);
        }
        return OptionalDouble.empty();
    }

    /** Credit bureau requests in the last days: 7 only. */
    public OptionalDouble bureauRequests(int days) {
        return days == 7 ? OptionalDouble.of(bureauRequestsLast7Days) : OptionalDouble.empty();
    }

    /** Credits opened in the last days: 30 only. */
    public OptionalDouble newCredits(int days) {
        return days == 30 ? OptionalDouble.of(newCreditsLast30Days) : OptionalDouble.empty();
    }
}
