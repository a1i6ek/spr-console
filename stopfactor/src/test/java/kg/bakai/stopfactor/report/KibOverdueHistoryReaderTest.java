package kg.bakai.stopfactor.report;

import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.CalendarItem;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.PaymentCalendarList;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract;
import kg.bakai.spr.core.kib.dto.response.Contracts.ContractList;
import kg.bakai.spr.core.kib.dto.response.Contracts;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader.OverdueHistory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static kg.bakai.spr.core.kib.dto.enums.RoleOfClient.CoDebtor;
import static kg.bakai.spr.core.kib.dto.enums.RoleOfClient.Guarantor;
import static kg.bakai.spr.core.kib.dto.enums.RoleOfClient.MainDebtor;
import static org.assertj.core.api.Assertions.assertThat;

class KibOverdueHistoryReaderTest {

    /** November 2026 is month 1, October month 2, and so on. */
    private static final LocalDate REQUEST_DAY = LocalDate.of(2026, 11, 4);
    private static final LocalDateTime REQUEST_DATE = REQUEST_DAY.atStartOfDay();

    private final KibOverdueHistoryReader calculator = new KibOverdueHistoryReader();

    @Nested
    class Report {

        @Test
        void noReportOrNoContractsMeansNoOverdueHistory() {
            OverdueHistory none = OverdueHistory.NONE;

            assertThat(calculator.read(null, REQUEST_DATE)).isEqualTo(none);
            assertThat(calculator.read(new CustomReport(), REQUEST_DATE)).isEqualTo(none);
            assertThat(calculator.read(report(), REQUEST_DATE)).isEqualTo(none);
            assertThat(calculator.read(new CustomReport(new Contracts(new ContractList(null))), REQUEST_DATE)).isEqualTo(none);
        }

        @Test
        void contractsWithoutCalendarsHaveNoOverdue() {
            Contract noCalendar = new Contract(MainDebtor, null, null);
            Contract emptyCalendar = new Contract(MainDebtor, null, new PaymentCalendarList(List.of()));

            assertThat(calculator.read(report(noCalendar, emptyCalendar), REQUEST_DATE)).isEqualTo(OverdueHistory.NONE);
        }

        @Test
        void contractWithoutCalendarIsSkippedWhenOthersHaveOne() {
            Contract noCalendar = new Contract(MainDebtor, null, null);

            assertThat(months(report(noCalendar, contract(MainDebtor, repaid(2026, 10, 12)))))
                    .containsExactly(new OverdueMonth(2, 12));
        }

        @Test
        void onlyMainDebtorContractsCount() {
            // as KibOverdueCalculator: an overdue of a loan the client guarantees is not the client's
            assertThat(months(report(
                    contract(MainDebtor, repaid(2026, 10, 5)),
                    contract(Guarantor, repaid(2026, 10, 6)),
                    contract(CoDebtor, repaid(2026, 10, 7)))))
                    .containsExactly(new OverdueMonth(2, 5));
        }

        @Test
        void guarantorAndCoDebtorContractsAloneMeanNoOverdueHistory() {
            Contract guarantorWithoutCalendar = new Contract(Guarantor, null, null);

            assertThat(calculator.read(report(contract(CoDebtor, repaid(2026, 10, 30)),
                    contract(Guarantor, repaid(2026, 9, 90)), guarantorWithoutCalendar), REQUEST_DATE))
                    .isEqualTo(OverdueHistory.NONE);
        }
    }

    @Nested
    class Months {

        @Test
        void overdueRepaidWithinTheMonthIsAMonthWithOverdue() {
            // the real report: MaxPastDueDays 18, PastDueDays 0 at the end of the month
            assertThat(months(repaid(2026, 7, 18), none(2026, 8))).containsExactly(new OverdueMonth(5, 18));
        }

        @Test
        void overdueGoingOnIntoTheNextMonthCountsInEveryMonth() {
            // as KibOverdueCalculator: 3 days at the end of August, 12 days in September - two months
            assertThat(months(open(2026, 8, 3, 3), repaid(2026, 9, 12), none(2026, 10)))
                    .containsExactly(new OverdueMonth(4, 3), new OverdueMonth(3, 12));
        }

        @Test
        void overdueOpenInTheMonthOfTheRequestCountsWithTheDaysSoFar() {
            assertThat(months(open(2026, 10, 4, 4), open(2026, 11, 18, 18)))
                    .containsExactly(new OverdueMonth(2, 4), new OverdueMonth(1, 18));
        }

        @Test
        void monthsAfterTheMonthOfTheRequestAreIgnored() {
            // the November entry is dated November 30, after the request day, and still counts
            assertThat(months(repaid(2026, 11, 7), repaid(2026, 12, 60))).containsExactly(new OverdueMonth(1, 7));
        }

        @Test
        void monthsAreReadInDateOrderAsKibSendsThemNewestFirst() {
            assertThat(months(none(2026, 10), repaid(2026, 9, 7), open(2026, 8, 3, 3)))
                    .containsExactly(new OverdueMonth(4, 3), new OverdueMonth(3, 7));
        }

        @Test
        void missingDatesAndDaysAreSkipped() {
            CalendarItem noDate = new CalendarItem(null, 50, 50);
            CalendarItem noDays = new CalendarItem(endOfMonth(2026, 8), null, null);
            CalendarItem onlyPastDueDays = new CalendarItem(endOfMonth(2026, 10), 6, null);

            assertThat(months(noDate, noDays, onlyPastDueDays, null)).containsExactly(new OverdueMonth(2, 6));
        }

        @Test
        void monthsOfAllContractsAreCollected() {
            assertThat(months(report(
                    contract(MainDebtor, repaid(2026, 10, 5)),
                    contract(MainDebtor, repaid(2026, 3, 40)))))
                    .containsExactly(new OverdueMonth(2, 5), new OverdueMonth(9, 40));
        }
    }

    /** A month the payment calendars do not show had no overdue (analyst's decision of 09.11.2026). */
    @Nested
    class MonthsWithoutCalendar {

        @Test
        void aCalendarShorterThanItsContractShowsNoOverdueBeforeIt() {
            Contract since2020 = new Contract(MainDebtor, LocalDate.of(2020, 1, 10).atStartOfDay(),
                    calendar(none(2024, 12), repaid(2026, 7, 25), none(2026, 11)));

            // December 2024 .. November 2026: only July 2026 (month 5) had overdue, months 25..36 count as none
            assertThat(months(report(since2020))).containsExactly(new OverdueMonth(5, 25));
        }

        @Test
        void aContractWithoutCalendarIsSkippedAndTheOthersAreCounted() {
            Contract withoutCalendar = new Contract(MainDebtor, LocalDate.of(2019, 5, 1).atStartOfDay(), null);
            Contract own = contract(MainDebtor, repaid(2026, 10, 3));

            assertThat(months(report(withoutCalendar, own))).containsExactly(new OverdueMonth(2, 3));
        }

        @Test
        void calendarsOfDifferentLengthsAreCountedAsTheyAre() {
            Contract twelveMonths = new Contract(MainDebtor, null, calendar(none(2025, 12), repaid(2026, 11, 4)));
            Contract twentyFourMonths = new Contract(MainDebtor, LocalDate.of(2019, 5, 1).atStartOfDay(),
                    calendar(repaid(2024, 12, 40), none(2026, 11)));

            assertThat(months(report(twentyFourMonths, twelveMonths)))
                    .containsExactlyInAnyOrder(new OverdueMonth(24, 40), new OverdueMonth(1, 4));
        }

        @Test
        void monthsAgoIsCountedInCalendarMonths() {
            YearMonth request = YearMonth.from(REQUEST_DAY);

            assertThat(KibOverdueHistoryReader.monthsAgo(request, request)).isEqualTo(1);
            assertThat(KibOverdueHistoryReader.monthsAgo(YearMonth.of(2026, 10), request)).isEqualTo(2);
            assertThat(KibOverdueHistoryReader.monthsAgo(YearMonth.of(2026, 9), request)).isEqualTo(3);
            assertThat(KibOverdueHistoryReader.monthsAgo(YearMonth.of(2026, 8), request)).isEqualTo(4);
            assertThat(KibOverdueHistoryReader.monthsAgo(YearMonth.of(2024, 12), request)).isEqualTo(24);
        }
    }

    /**
     * The stop factors with the params of a legacy counter get exactly that counter: the counts of the overdue months
     * are compared with a copy of the KIB module's KibOverdueCalculator on random calendars.
     */
    @Nested
    class SameAsKibModule {

        private static final int[][] PERIODS = {{1, 3}, {4, 6}, {7, 12}, {13, 24}, {25, 36}};
        private static final int[][] RANGE_UPPER_BOUNDS = {{15, 30}, {15, 30}, {10, 20, 30}, {10, 20, 30}, {60, 90}};

        @Test
        void legacyCountersFromTheOverdueMonths() {
            Random random = new Random(7);
            for (int application = 0; application < 500; application++) {
                List<Contract> contracts = new ArrayList<>();
                for (int c = 0; c < 1 + random.nextInt(4); c++) {
                    RoleOfClient role = List.of(MainDebtor, MainDebtor, Guarantor, CoDebtor).get(random.nextInt(4));
                    List<CalendarItem> items = new ArrayList<>();
                    for (int m = 0; m < 40; m++) {
                        int days = random.nextInt(4) == 0 ? 1 + random.nextInt(120) : 0;
                        items.add(new CalendarItem(endOfMonth(2026, 12).minusMonths(m), random.nextBoolean() ? days : 0, days));
                    }
                    contracts.add(new Contract(role, LocalDateTime.of(2020, 1, 1, 0, 0), calendar(items.toArray(CalendarItem[]::new))));
                }
                CustomReport report = report(contracts.toArray(Contract[]::new));

                List<OverdueMonth> months = months(report);
                int[] expected = kibOverdueCalculator(report);
                int field = 0;
                for (int p = 0; p < PERIODS.length; p++) {
                    int from = 1;
                    for (int r = 0; r <= RANGE_UPPER_BOUNDS[p].length; r++) {
                        int min = from;
                        Integer max = r < RANGE_UPPER_BOUNDS[p].length ? RANGE_UPPER_BOUNDS[p][r] : null;
                        int periodFrom = PERIODS[p][0];
                        int periodTo = PERIODS[p][1];
                        long counted = months.stream().filter(month -> month.monthsAgo() >= periodFrom && month.monthsAgo() <= periodTo
                                && month.maxOverdueDays() >= min && (max == null || month.maxOverdueDays() <= max)).count();
                        assertThat(counted).as("application %s, counter %s", application, field).isEqualTo(expected[field]);
                        field++;
                        from = max != null ? max + 1 : from;
                    }
                }
            }
        }

        /** Copy of KibOverdueCalculator.getOverdueByPeriodAndCount of the KIB module, the 17 counters in field order. */
        private static int[] kibOverdueCalculator(CustomReport customReport) {
            int[] counts = new int[17];
            for (Contract contract : customReport.contracts().contractList().contracts()) {
                if (contract.roleOfClient() != RoleOfClient.MainDebtor) continue;
                if (contract.paymentCalendarList() == null) continue;
                for (CalendarItem calendar : contract.paymentCalendarList().paymentCalendarItems()) {
                    int overdue = calendar.maxPastDueDays();
                    if (overdue < 1) continue;
                    long month = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(calendar.date()), YearMonth.from(REQUEST_DATE)) + 1;
                    int field = 0;
                    for (int p = 0; p < PERIODS.length; p++) {
                        int ranges = RANGE_UPPER_BOUNDS[p].length + 1;
                        if (PERIODS[p][0] <= month && month <= PERIODS[p][1]) {
                            int index = RANGE_UPPER_BOUNDS[p].length;
                            for (int i = 0; i < RANGE_UPPER_BOUNDS[p].length; i++) {
                                if (overdue <= RANGE_UPPER_BOUNDS[p][i]) {
                                    index = i;
                                    break;
                                }
                            }
                            counts[field + index]++;
                            break;
                        }
                        field += ranges;
                    }
                }
            }
            return counts;
        }
    }

    /** The contracts of a real KIB report (kib/custom-report-contracts.json, without personal data). */
    @Nested
    class RealKibReport {

        private static final LocalDateTime REPORT_DATE = LocalDateTime.of(2025, 4, 25, 14, 35, 5);

        @Test
        void reportIsReadFromTheKibJson() throws IOException {
            CustomReport report = realReport();

            List<Contract> contracts = report.contracts().contractList().contracts();
            assertThat(contracts).hasSize(6).extracting(Contract::roleOfClient).containsOnly(MainDebtor);
            assertThat(contracts.getFirst().startDate()).isEqualTo(LocalDateTime.of(2024, 3, 19, 0, 0));
            assertThat(contracts.getFirst().paymentCalendarList().paymentCalendarItems()).hasSize(24)
                    .first().isEqualTo(new CalendarItem(LocalDateTime.of(2025, 4, 30, 0, 0), 0, 0));
        }

        @Test
        void overduesRepaidWithinTheMonthShowOnlyInMaxPastDueDays() throws IOException {
            List<CalendarItem> items = realReport().contracts().contractList().contracts().stream()
                    .flatMap(contract -> contract.paymentCalendarList().paymentCalendarItems().stream())
                    .toList();

            assertThat(items).extracting(CalendarItem::pastDueDays).containsOnly(0);
            assertThat(items).extracting(CalendarItem::maxPastDueDays).contains(18, 8, 2, 1);
        }

        @Test
        void overdueMonthsOfTheRealReport() throws IOException {
            OverdueHistory history = calculator.read(realReport(), REPORT_DATE);

            // April 2025 is month 1. Contract 1: May and June 2024, 2 days each.
            // Contract 6: October 2023 (2), December 2023 (1), February 2024 (8), April 2024 (8), May 2024 (18),
            // July 2024 (1)
            assertThat(history.months()).containsExactly(
                    new OverdueMonth(12, 2), new OverdueMonth(11, 2),
                    new OverdueMonth(19, 2), new OverdueMonth(17, 1), new OverdueMonth(15, 8),
                    new OverdueMonth(13, 8), new OverdueMonth(12, 18), new OverdueMonth(10, 1));
        }

        private static CustomReport realReport() throws IOException {
            try (InputStream json = KibOverdueHistoryReaderTest.class.getResourceAsStream("/kib/custom-report-contracts.json")) {
                return JsonMapper.builder().build().readValue(Objects.requireNonNull(json), CustomReport.class);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------

    private List<OverdueMonth> months(CalendarItem... items) {
        return months(report(contract(MainDebtor, items)));
    }

    private List<OverdueMonth> months(CustomReport report) {
        return calculator.read(report, REQUEST_DATE).months();
    }

    /** A month with an overdue repaid within it, as KIB sends it: on the last day of the month. */
    private static CalendarItem repaid(int year, int month, int maxPastDueDays) {
        return new CalendarItem(endOfMonth(year, month), 0, maxPastDueDays);
    }

    /** A month whose overdue is not repaid by its end: pastDueDays left, goes on into the next month. */
    private static CalendarItem open(int year, int month, int maxPastDueDays, int pastDueDays) {
        return new CalendarItem(endOfMonth(year, month), pastDueDays, maxPastDueDays);
    }

    private static CalendarItem none(int year, int month) {
        return new CalendarItem(endOfMonth(year, month), 0, 0);
    }

    private static LocalDateTime endOfMonth(int year, int month) {
        return YearMonth.of(year, month).atEndOfMonth().atStartOfDay();
    }

    /** A contract whose calendar starts with it. */
    private static Contract contract(RoleOfClient role, CalendarItem... items) {
        LocalDateTime start = Arrays.stream(items).filter(Objects::nonNull).map(CalendarItem::date).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        return new Contract(role, start, calendar(items));
    }

    private static PaymentCalendarList calendar(CalendarItem... items) {
        return new PaymentCalendarList(new ArrayList<>(Arrays.asList(items)));
    }

    private static CustomReport report(Contract... contracts) {
        return new CustomReport(new Contracts(new ContractList(new ArrayList<>(Arrays.asList(contracts)))));
    }
}
