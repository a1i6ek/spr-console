package kg.bakai.stopfactor;

import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.GeneralInfoDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.kib.dto.enums.LoanPhase;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.spr.core.kib.dto.response.Contracts;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.CalendarItem;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.PaymentCalendarList;
import kg.bakai.spr.core.kib.dto.response.Contracts.ContractList;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.StopFactorConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.service.StopFactorService;
import kg.bakai.stopfactor.service.impl.StopFactorServiceImpl;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCheck;
import kg.bakai.stopfactor.entity.StopFactorCondition;
import kg.bakai.stopfactor.entity.StopFactorRule;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;
import kg.bakai.stopfactor.dto.StopFactorProjection;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.ToIntFunction;

import static java.time.temporal.ChronoUnit.MONTHS;
import static kg.bakai.stopfactor.enums.StopFactorStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the legacy SPR algorithm (a copy of StopFactorLegacyServiceImpl) and the new StopFactorServiceImpl
 * on the same KIB/OB reports against the migrated database (stop factors 1..34 from changelog-1.2, metrics and
 * params from changelog-1.3) and checks that every stop factor gets the same result and checked
 * value, and the application the same decision. Also checks scorings made before and after a stop factor change.
 * <p>
 * The legacy algorithm only knows ids 1..34, so every test first brings the stop factors back to the state of
 * changelog 1.3 ({@link #rewindToChangelog13()}). The overdue stop factors read the same KIB payment calendars in
 * both: the legacy through the KIB module's counters ({@link KibOverdueCalculator}, a copy), the new service through
 * KibOverdueHistoryReader. Every test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Transactional
class LegacyStopFactorParityTest {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 3);

    @Autowired
    private StopFactorService stopFactorService;
    @Autowired
    private StopFactorRepository stopFactorRepository;
    @Autowired
    private StopFactorCheckRepository stopFactorCheckRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long scoringId = -1_000_000;

    @BeforeEach
    void setUp() {
        rewindToChangelog13();
        // the 34 legacy stop factors are the rules 1..34 of 10 stop factors (#4: restructurings, #13: overdue history)
        List<StopFactor> all = stopFactorRepository.findAll();
        assertThat(all).extracting(StopFactor::getId).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 8L, 9L, 10L, 11L, 12L, 13L);
        assertThat(all).allMatch(sf -> sf.getVersionNumber() == 1 && sf.getSegment() == null);
        assertThat(all.stream().flatMap(sf -> sf.getRules().stream()).map(StopFactorRule::getId).toList())
                .containsExactlyInAnyOrderElementsOf(java.util.stream.LongStream.rangeClosed(1, 34).boxed().toList());
    }

    /**
     * Removes every version created after changelog 1.3 (changelog 1.4, console edits) and makes ids 1..34 current
     * again. Rolled back with the test.
     */
    private void rewindToChangelog13() {
        String newerRules = "SELECT id FROM stop_factors.stop_factor_rule WHERE stop_factor_id > 34";
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_condition WHERE rule_id IN (" + newerRules + ")");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_check WHERE rule_id IN (" + newerRules + ")");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_rule WHERE stop_factor_id > 34");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_product WHERE stop_factor_id > 34");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor WHERE id > 34");
        jdbcTemplate.update("UPDATE stop_factors.stop_factor SET valid_to = NULL WHERE id <= 34");
    }

    // ------------------------------------------------------------------------------------------------
    // Same results as the legacy algorithm
    // ------------------------------------------------------------------------------------------------

    /**
     * Every rule of a legacy overdue stop factor that reads a KIB counter has the params of that counter's range,
     * as its production name says (#27 "1-15 days" reads the KIB counter of months 1..3, 1-15 days).
     */
    @Test
    void legacyOverdueStopFactorsMeasureTheKibCounterTheyRead() {
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
                SELECT r.id, r.name,
                       (r.params->>'periodFromMonth')::int AS period_from, (r.params->>'periodToMonth')::int AS period_to,
                       (r.params->>'minOverdueDays')::int AS min_days, (r.params->>'maxOverdueDays')::int AS max_days
                FROM stop_factors.stop_factor_rule r
                WHERE r.id BETWEEN 17 AND 34 AND r.id <> 19""");

        assertThat(versions).hasSize(17);
        List<List<Integer>> kibBands = KibOverdueCalculator.bands();
        for (Map<String, Object> v : versions) {
            assertThat(kibBands).as("legacy stop factor %s", v.get("id")).contains(Arrays.asList(
                    (Integer) v.get("period_from"), (Integer) v.get("period_to"), (Integer) v.get("min_days"), (Integer) v.get("max_days")));
        }
        Map<String, Object> stopFactor27 = versions.stream().filter(v -> v.get("id").equals(27L)).findFirst().orElseThrow();
        assertThat(stopFactor27).containsEntry("min_days", 1).containsEntry("max_days", 15);
        assertThat((String) stopFactor27.get("name")).endsWith("с 1 го по 3 месяц, где максимальная просрочка >= 1 и <= 15");
    }

    @Test
    void everyLegacyStopFactorReadsItsOwnValue() {
        // every value is different: a stop factor reading the wrong value would show another number
        Reports reports = new Reports(
                new GeneralInfoDto(1, 2, 7),
                List.of(LoanDto.builder()
                        .participationType(RoleOfClient.MainDebtor)
                        .loanStatus(LoanStatus.Open)
                        .numberOfRestructuringsLast12Months(3)
                        .numberOfRestructuringsFrom12To24Months(5)
                        .daysDelay(9)
                        .phaseOfLoan(LoanPhase.Open)
                        .build()),
                new ConsolidationDto(new BigDecimal("9.5"), 10, 11),
                List.of(new TotalDebtReportData(4, new BigDecimal("0.25"), REQUEST_DATE.minusMonths(2)),
                        new TotalDebtReportData(0, null, REQUEST_DATE.minusMonths(14)),
                        new TotalDebtReportData(0, null, REQUEST_DATE.minusMonths(20))),
                contractsWithADifferentCountPerBand());

        assertSameAsLegacy(reports);
    }

    @Test
    void randomApplicationsGetTheLegacyResult() {
        Random random = new Random(42);
        Set<StopFactorStatus> statusesSeen = EnumSet.noneOf(StopFactorStatus.class);
        Set<InternalStatusCode> codesSeen = new HashSet<>();

        for (int i = 0; i < 300; i++) {
            // how often a value is not 0 in this application: nearly clean, some risk, risky
            int percent = List.of(2, 8, 25).get(random.nextInt(3));
            StopFactorDecision decision = assertSameAsLegacy(randomReports(random, percent));
            statusesSeen.add(decision.status());
            codesSeen.add(decision.internalStatusCode());
        }

        // the random data must have exercised every outcome, otherwise the comparison proves little
        assertThat(statusesSeen).containsExactlyInAnyOrder(APPROVED, UNDERWRITER, REJECTED);
        assertThat(codesSeen).contains(InternalStatusCode._451, null);
    }

    @Test
    void applicationWithoutKibReportGetsTheLegacyResult() {
        assertSameAsLegacy(new Reports(null, null, null, List.of()));
        assertSameAsLegacy(new Reports(null, null, null, List.of(new TotalDebtReportData(5, null, null))));
        assertSameAsLegacy(new Reports(null, null, null, List.of(new TotalDebtReportData(0, new BigDecimal("1500.75"), null))));
    }

    // ------------------------------------------------------------------------------------------------
    // Legacy and new scorings, new structure
    // ------------------------------------------------------------------------------------------------

    @Test
    void scoringsBeforeAndAfterAStopFactorChangeKeepTheirOwnVersion() {
        Reports overdue3Days = new Reports(null, null, null, List.of(new TotalDebtReportData(3, null, null)));

        // scoring made with the legacy stop factor 9: REJECTED when overdue days > 0
        long legacyScoring = nextScoringId();
        assertThat(check(legacyScoring, overdue3Days)).isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));

        // the condition is changed: version 2 of stop factor 9 rejects only above 5 days
        stopFactorService.saveCondition(new StopFactorConditionDto(9, "5", StopFactorValueTypes.INTEGER, REJECTED));
        StopFactor version2 = stopFactorRepository.findCurrent(9).orElseThrow();
        assertThat(version2.getId()).isNotEqualTo(9L);
        assertThat(version2.getVersionNumber()).isEqualTo(2);

        long newScoring = nextScoringId();
        assertThat(check(newScoring, overdue3Days)).isEqualTo(new StopFactorDecision(APPROVED, null));

        StopFactorProjection legacyRow = overdueDaysRow(legacyScoring);
        assertThat(legacyRow.getStopFactorId()).isEqualTo(9L);
        assertThat(legacyRow.getStopFactorVersionId()).isEqualTo(9L);
        assertThat(legacyRow.getVersionNumber()).isEqualTo(1);
        assertThat(legacyRow.getCheckResult()).isEqualTo(REJECTED);
        assertThat(legacyRow.getRejectedValue()).isEqualTo("0");

        StopFactorProjection newRow = overdueDaysRow(newScoring);
        assertThat(newRow.getStopFactorId()).isEqualTo(9L);
        assertThat(newRow.getStopFactorVersionId()).isEqualTo(version2.getId());
        assertThat(newRow.getVersionNumber()).isEqualTo(2);
        assertThat(newRow.getCheckResult()).isEqualTo(APPROVED);
        assertThat(newRow.getRejectedValue()).isEqualTo("5");
    }

    @Test
    void stopFactorAddedWithSaveIsChecked() {
        stopFactorService.save("Количество активных споров КИБ", "Добавлен через StopFactorService.save");
        StopFactor added = stopFactorRepository.findCurrentActiveWithMetric().stream()
                .filter(sf -> sf.getId() > 34)
                .findFirst().orElseThrow();
        stopFactorService.saveCondition(new StopFactorConditionDto(added.getId(), "1", StopFactorValueTypes.INTEGER, UNDERWRITER));

        // 1 active dispute: legacy stop factor 2 (REJECTED > 0) rejects, the new one (UNDERWRITER > 1) does not fire
        Reports reports = new Reports(new GeneralInfoDto(2, 0, 0), List.of(), new ConsolidationDto(BigDecimal.ZERO, 0, 0), List.of());
        long scoring = nextScoringId();
        check(scoring, reports);

        StopFactorProjection row = findChecks(scoring).stream()
                .filter(p -> p.getStopFactorId() == added.getId())
                .findFirst().orElseThrow();
        assertThat(row.getCheckedValue()).isEqualTo("2.0");
        assertThat(row.getCheckResult()).isEqualTo(UNDERWRITER);
        assertThat(row.getVersionNumber()).isEqualTo(2);
    }

    @Test
    void stopFactorLimitedToAnotherProductIsSkipped() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description) VALUES (-1, 'Test A', 'A'), (-2, 'Test B', 'B')");
        jdbcTemplate.update("INSERT INTO public.scoring (id, product_id) VALUES (-500, -1), (-501, -2)");
        jdbcTemplate.update("INSERT INTO stop_factors.stop_factor_product (stop_factor_id, product_id) VALUES (2, -2)");
        Reports reports = new Reports(null, null, null, List.of());

        check(-500, reports);
        check(-501, reports);

        assertThat(findChecks(-500)).extracting(StopFactorProjection::getStopFactorId).doesNotContain(2L);
        assertThat(findChecks(-501)).extracting(StopFactorProjection::getStopFactorId).contains(2L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT DISTINCT product_id FROM stop_factors.stop_factor_check WHERE scoring_id = -500", Long.class))
                .containsExactly(-1L);
    }

    /**
     * Legacy returned a hardcoded 0 for 13, 14, 15, 16, 19 (inactive since 2024): their periods have no KIB counter.
     * The new service counts the calendars for them, so if someone activates them a client without overdue gets the
     * same 0 as in legacy, not NO_DATA.
     */
    @Test
    void inactiveLegacyStopFactorsWithoutACounterGetTheLegacyZeroIfActivated() {
        // #13..#16, #19 are inactive rules of the overdue history stop factor #13 (version 1 after the rewind)
        List<Long> ids = List.of(13L, 14L, 15L, 16L, 19L);
        stopFactorRepository.findCurrent(13).orElseThrow().getRules().stream()
                .filter(rule -> ids.contains(rule.getId()))
                .forEach(rule -> rule.setActive(true));
        stopFactorRepository.flush();

        assertSameAsLegacy(new Reports(new GeneralInfoDto(0, 0, 0), List.of(), new ConsolidationDto(BigDecimal.ZERO, 0, 0), List.of()));
    }

    @Test
    void legacyStopFactorsOfOneMetricAreRulesOfOneStopFactor() {
        StopFactor restructurings = stopFactorRepository.findCurrent(4).orElseThrow();
        assertThat(restructurings.getName()).isEqualTo("Количество реструктуризаций");
        assertThat(restructurings.getRules()).extracting(StopFactorRule::getId).containsExactly(4L, 5L, 6L, 7L);

        StopFactor overdue = stopFactorRepository.findCurrent(13).orElseThrow();
        assertThat(overdue.getName()).isEqualTo("Количество непрерывной просроченной задолженности");
        assertThat(overdue.getRules()).hasSize(22);
        assertThat(overdue.getRules()).filteredOn(StopFactorRule::isActive).extracting(StopFactorRule::getId)
                .containsExactly(27L, 28L, 29L, 30L, 31L, 32L, 17L, 18L, 33L, 34L, 20L, 21L, 22L, 23L, 24L, 25L, 26L);
        for (long merged = 5; merged <= 34; merged++) {
            if (merged != 8 && merged != 9 && merged != 10 && merged != 11 && merged != 12 && merged != 13) {
                assertThat(stopFactorRepository.findById(merged)).as("stop factor " + merged).isEmpty();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------

    /** @param contracts the KIB contracts with their payment calendars, the source of the overdue stop factors */
    private record Reports(GeneralInfoDto generalInfo, List<LoanDto> loans, ConsolidationDto consolidation,
                           List<TotalDebtReportData> obCredits, List<Contract> contracts) {

        /** Without KIB contracts: no overdue. */
        Reports(GeneralInfoDto generalInfo, List<LoanDto> loans, ConsolidationDto consolidation,
                List<TotalDebtReportData> obCredits) {
            this(generalInfo, loans, consolidation, obCredits, List.of());
        }

        CustomReport customReport() {
            return generalInfo != null ? new CustomReport(new Contracts(new ContractList(contracts))) : null;
        }

        IndividualReport individualReport() {
            return generalInfo != null ? new IndividualReport(generalInfo, loans) : null;
        }

        TotalDebtFinalResponse totalDebt() {
            return new TotalDebtFinalResponse(obCredits);
        }
    }

    private StopFactorDecision check(long scoringId, Reports reports) {
        return stopFactorService.check(scoringId, reports.customReport(), reports.individualReport(),
                reports.consolidation(), reports.totalDebt(), REQUEST_DATE);
    }

    private StopFactorDecision assertSameAsLegacy(Reports reports) {
        long scoring = nextScoringId();
        LegacyResult legacy = new StopFactorLegacyService().check(scoring, reports.customReport(), reports.individualReport(),
                reports.consolidation(), reports.totalDebt(), REQUEST_DATE);

        StopFactorDecision decision = check(scoring, reports);

        assertThat(findChecks(scoring)).as(reports.toString())
                .extracting(p -> p.getRuleName() + " | " + p.getCheckResult() + " | " + p.getCheckedValue())
                .containsExactlyElementsOf(legacy.checks().stream()
                        .map(c -> c.getRule().getName() + " | " + c.getResult() + " | " + c.getCheckedValue())
                        .toList());
        assertThat(decision).as(reports.toString()).isEqualTo(legacy.decision());
        return decision;
    }

    private List<StopFactorProjection> findChecks(long scoringId) {
        stopFactorCheckRepository.flush();
        return stopFactorService.findByScoringId(scoringId);
    }

    private StopFactorProjection overdueDaysRow(long scoringId) {
        return findChecks(scoringId).stream().filter(p -> p.getStopFactorId() == 9L).findFirst().orElseThrow();
    }

    private long nextScoringId() {
        return scoringId--;
    }

    // 0 in most cases, so that approved and underwriter outcomes happen as well as rejected ones
    private static int rarely(Random random, int percent, int max) {
        return random.nextInt(100) >= percent ? 0 : 1 + random.nextInt(max);
    }

    /**
     * KIB contracts of any role, some without a payment calendar, with a calendar of 12 or 24 months that may be
     * shorter than the contract, and overdue in some months.
     */
    private static List<Contract> randomContracts(Random random, int percent) {
        List<Contract> contracts = new ArrayList<>();
        for (int c = random.nextInt(4); c > 0; c--) {
            RoleOfClient role = List.of(RoleOfClient.MainDebtor, RoleOfClient.MainDebtor, RoleOfClient.Guarantor,
                    RoleOfClient.CoDebtor).get(random.nextInt(4));
            LocalDateTime start = REQUEST_DATE.minusMonths(1 + random.nextInt(48)).atStartOfDay();
            if (random.nextInt(8) == 0) {
                contracts.add(new Contract(role, start, null));
                continue;
            }
            List<CalendarItem> calendar = new ArrayList<>();
            for (int monthsAgo = random.nextBoolean() ? 11 : 23; monthsAgo >= 0; monthsAgo--) {
                int days = rarely(random, percent, 120);
                calendar.add(new CalendarItem(endOfMonth(REQUEST_DATE.minusMonths(monthsAgo)), random.nextBoolean() ? days : 0, days));
            }
            contracts.add(new Contract(role, start, new PaymentCalendarList(calendar)));
        }
        return contracts;
    }

    /** Band k of the KIB counters gets k + 1 overdue months, each in a contract of its own. */
    private static List<Contract> contractsWithADifferentCountPerBand() {
        List<Contract> contracts = new ArrayList<>();
        List<List<Integer>> bands = KibOverdueCalculator.bands();
        for (int k = 0; k < bands.size(); k++) {
            int periodFrom = bands.get(k).get(0);
            int minDays = bands.get(k).get(2);
            for (int i = 0; i <= k; i++) {
                LocalDateTime month = endOfMonth(REQUEST_DATE.minusMonths(periodFrom - 1L));
                contracts.add(new Contract(RoleOfClient.MainDebtor, month,
                        new PaymentCalendarList(List.of(new CalendarItem(month, 0, minDays)))));
            }
        }
        return contracts;
    }

    private static LocalDateTime endOfMonth(LocalDate day) {
        return YearMonth.from(day).atEndOfMonth().atStartOfDay();
    }

    private static Reports randomReports(Random random, int percent) {
        List<TotalDebtReportData> obCredits = new ArrayList<>();
        for (int i = random.nextInt(3); i > 0; i--) {
            obCredits.add(new TotalDebtReportData(
                    rarely(random, percent, 30),
                    random.nextInt(100) >= percent ? null : new BigDecimal(random.nextInt(100_000)).movePointLeft(2),
                    random.nextInt(4) == 0 ? null : REQUEST_DATE.minusMonths(random.nextInt(30))));
        }
        if (random.nextInt(10) == 0) {
            return new Reports(null, null, null, obCredits);   // no KIB report
        }
        List<LoanDto> loans = new ArrayList<>();
        for (int i = random.nextInt(4); i > 0; i--) {
            loans.add(LoanDto.builder()
                    .participationType(RoleOfClient.values()[random.nextInt(RoleOfClient.values().length)])
                    .loanStatus(random.nextInt(100) >= percent ? LoanStatus.Open : LoanStatus.values()[random.nextInt(LoanStatus.values().length)])
                    .numberOfRestructuringsLast12Months(rarely(random, percent, 1))
                    .numberOfRestructuringsFrom12To24Months(rarely(random, percent, 1))
                    .daysDelay(rarely(random, percent, 30))
                    .phaseOfLoan(random.nextInt(4) == 0 ? LoanPhase.Closed : LoanPhase.Open)
                    .delayAmount(random.nextInt(100) >= percent ? 0 : random.nextInt(100_000) / 100.0)
                    .overdueInterest(random.nextInt(100) >= percent ? 0 : random.nextInt(10_000) / 100.0)
                    .build());
        }
        return new Reports(
                new GeneralInfoDto(rarely(random, percent, 1), rarely(random, percent, 1), rarely(random, percent, 15) * 2),
                loans,
                new ConsolidationDto(random.nextInt(100) >= percent ? BigDecimal.ZERO : new BigDecimal(random.nextInt(100_000)).movePointLeft(2),
                        rarely(random, percent, 8), rarely(random, percent, 15)),
                obCredits,
                randomContracts(random, percent));
    }

    private final KibOverdueCalculator kibOverdueCalculator = new KibOverdueCalculator();

    private record LegacyResult(StopFactorDecision decision, List<StopFactorCheck> checks) {
    }

    /** Copy of OverdueForStopFactor of the KIB module: the 17 counters the legacy algorithm reads. */
    private record OverdueForStopFactor(
            int countWherePeriodFrom1To3MonthsAndOverdueFrom1To15,
            int countWherePeriodFrom1To3MonthsAndOverdueFrom16To30,
            int countWherePeriodFrom1To3MonthsAndOverdueFrom31,
            int countWherePeriodFrom4To6MonthsAndOverdueFrom1To15,
            int countWherePeriodFrom4To6MonthsAndOverdueFrom16To30,
            int countWherePeriodFrom4To6MonthsAndOverdueFrom31,
            int countWherePeriodFrom7To12MonthsAndOverdueFrom1To10,
            int countWherePeriodFrom7To12MonthsAndOverdueFrom11To20,
            int countWherePeriodFrom7To12MonthsAndOverdueFrom21To30,
            int countWherePeriodFrom7To12MonthsAndOverdueFrom31,
            int countWherePeriodFrom13To24MonthsAndOverdueFrom1To10,
            int countWherePeriodFrom13To24MonthsAndOverdueFrom11To20,
            int countWherePeriodFrom13To24MonthsAndOverdueFrom21To30,
            int countWherePeriodFrom13To24MonthsAndOverdueFrom31,
            int countWherePeriodFrom25To36MonthsAndOverdueFrom1To60,
            int countWherePeriodFrom25To36MonthsAndOverdueFrom61To90,
            int countWherePeriodFrom25To36MonthsAndOverdueFrom91
    ) {
        static final OverdueForStopFactor NONE = new OverdueForStopFactor(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Copy of KibOverdueCalculator of the KIB module (KibService.getOverdueByPeriodAndCount), the logic is not changed. */
    private static final class KibOverdueCalculator {

        private record Period(int fromMonth, int toMonth, int... rangeUpperBounds) {

            boolean contains(long month) {
                return fromMonth <= month && month <= toMonth;
            }

            int rangesCount() {
                return rangeUpperBounds.length + 1;
            }

            int rangeIndex(int overdue) {
                for (int i = 0; i < rangeUpperBounds.length; i++) {
                    if (overdue <= rangeUpperBounds[i]) {
                        return i;
                    }
                }
                return rangeUpperBounds.length;
            }
        }

        // Порядок периодов и диапазонов совпадает с порядком полей OverdueForStopFactor
        private static final List<Period> PERIODS = List.of(
                new Period(1, 3, 15, 30),        // 1-15, 16-30, 31+
                new Period(4, 6, 15, 30),        // 1-15, 16-30, 31+
                new Period(7, 12, 10, 20, 30),   // 1-10, 11-20, 21-30, 31+
                new Period(13, 24, 10, 20, 30),  // 1-10, 11-20, 21-30, 31+
                new Period(25, 36, 60, 90)       // 1-60, 61-90, 91+
        );

        /** The 17 counters as [periodFrom, periodTo, minDays, maxDays or null], in field order. Not in the KIB module. */
        static List<List<Integer>> bands() {
            List<List<Integer>> bands = new ArrayList<>();
            for (Period period : PERIODS) {
                int min = 1;
                for (int i = 0; i <= period.rangeUpperBounds().length; i++) {
                    Integer max = i < period.rangeUpperBounds().length ? period.rangeUpperBounds()[i] : null;
                    bands.add(Arrays.asList(period.fromMonth(), period.toMonth(), min, max));
                    min = max != null ? max + 1 : min;
                }
            }
            return bands;
        }

        OverdueForStopFactor getOverdueByPeriodAndCount(CustomReport customReport, LocalDateTime requestedDate) {
            int[][] counts = new int[PERIODS.size()][];
            for (int p = 0; p < PERIODS.size(); p++) {
                counts[p] = new int[PERIODS.get(p).rangesCount()];
            }

            if (customReport.contracts().contractList() != null) {
                for (var contract : customReport.contracts().contractList().contracts()) {

                    if (contract.roleOfClient() != RoleOfClient.MainDebtor) continue;
                    if (contract.paymentCalendarList() == null) continue;

                    for (var calendar : contract.paymentCalendarList().paymentCalendarItems()) {
                        int overdue = calendar.maxPastDueDays();
                        if (overdue < 1) continue;

                        // +1 к месяцу для того, чтобы учитывать нынешний месяц тоже
                        long month = MONTHS.between(YearMonth.from(calendar.date()), YearMonth.from(requestedDate)) + 1;

                        for (int p = 0; p < PERIODS.size(); p++) {
                            if (PERIODS.get(p).contains(month)) {
                                counts[p][PERIODS.get(p).rangeIndex(overdue)]++;
                                break;
                            }
                        }
                    }
                }
            }

            return new OverdueForStopFactor(
                    counts[0][0], counts[0][1], counts[0][2],
                    counts[1][0], counts[1][1], counts[1][2],
                    counts[2][0], counts[2][1], counts[2][2], counts[2][3],
                    counts[3][0], counts[3][1], counts[3][2], counts[3][3],
                    counts[4][0], counts[4][1], counts[4][2]
            );
        }
    }

    /** Copy of the legacy value DTO the legacy algorithm reads. */
    private record StopFactorIntegerValueDto(
            double numberOfActiveDisputes,
            double numberOfUnjustifiedDisputesInPast,
            double numberOfRestructuringsLast12Months,
            double countOfChangesObLast12Month,
            double numberOfRestructuringsFrom12To24Months,
            double countOfChangesObFrom13To24Months,
            double creditInquiriesLast7Days,
            double currentNumberOfDaysOverdue,
            double currentAmountOfOverdue,
            double totalNumberOfNewOpenLoansInTheLast30Days,
            double totalOpenCreditContracts,
            OverdueForStopFactor overdue
    ) {
    }

    /**
     * Copy of StopFactorLegacyServiceImpl. Differences:
     * <ul>
     *     <li>check() returns the checks instead of saving them, so they can be compared with the new service;</li>
     *     <li>without a full KIB report the OB changes are counted (initializeStopFactorIntegerValueDto): the legacy
     *     set them to 0, so stop factors #5 and #7 (OB restructurings) never fired without a KIB report. The new
     *     service counts them on purpose (LegacyCountersProvider);</li>
     *     <li>the current overdue amount (#10) adds the overdue of the open loans the client guarantees to the KIB
     *     consolidation, which counts the main debtor loans only. The current overdue days (#9) already included the
     *     guarantor's loans; the new service makes the amount the same on purpose (ReportValuesProvider).</li>
     * </ul>
     */
    private class StopFactorLegacyService {

        public LegacyResult check(
                long scoringId,
                CustomReport customReport, IndividualReport individualReport,
                ConsolidationDto consolidationDto, TotalDebtFinalResponse totalDebt, LocalDate requestDate
        ) {
            StopFactorStatus globalStopFactorStatus = APPROVED;
            InternalStatusCode internalStatusCode = null;
            StopFactorIntegerValueDto reportValues = initializeStopFactorIntegerValueDto(customReport, individualReport, consolidationDto, totalDebt, requestDate);

            var stopFactors = stopFactorRepository.findCurrentActiveWithMetric();
            List<StopFactorCheck> stopFactorChecks = new ArrayList<>();

            // a legacy stop factor is an active rule now; its rule id is the legacy id (changelog 1.7.0 and 1.7.3)
            for (var s : stopFactors.stream().flatMap(sf -> sf.getRules().stream()).filter(StopFactorRule::isActive).toList()) {
                double count = getVariableFunction(s.getId(), reportValues);
                StopFactorCheck stopFactorCheck = new StopFactorCheck(scoringId, s, String.valueOf(count));
                stopFactorCheck = getStopFactorCheck(scoringId, individualReport, s, stopFactorCheck, count, REJECTED);

                if (stopFactorCheck.getResult() != REJECTED) {
                    stopFactorCheck = getStopFactorCheck(scoringId, individualReport, s, stopFactorCheck, count, UNDERWRITER);
                }
                stopFactorChecks.add(stopFactorCheck);

                if (stopFactorCheck.getResult() == REJECTED) {
                    globalStopFactorStatus = REJECTED;

                    if (s.getId() == 9 || s.getId() == 10) internalStatusCode = InternalStatusCode._451;


                } else if (globalStopFactorStatus != REJECTED && stopFactorCheck.getResult() == UNDERWRITER) {
                    globalStopFactorStatus = UNDERWRITER;
                }
            }

            return new LegacyResult(new StopFactorDecision(globalStopFactorStatus, internalStatusCode), stopFactorChecks);
        }

        private StopFactorCheck getStopFactorCheck(long scoringId, IndividualReport individualReport, StopFactorRule s, StopFactorCheck stopFactorCheck, double count, StopFactorStatus expectedStatus) {
            StopFactorCondition condition;
            var optionalCondition = s.getConditions().stream()
                    .filter(c -> c.getResult() == expectedStatus)
                    .findFirst();
            if (optionalCondition.isPresent()) {
                condition = optionalCondition.get();
                if (condition.getValueType() == StopFactorValueTypes.ARRAY) {
                    String[] array = condition.getValue().split(", ");
                    List<LoanStatus> loanStatusList = getLoanStatuses(array);
                    var result = checkLoanNotValidStatuses(individualReport, expectedStatus, loanStatusList);
                    stopFactorCheck = new StopFactorCheck(scoringId, s, result.getValue());
                    stopFactorCheck.setResult(result.getStatus());
                } else if (condition.getValueType() == StopFactorValueTypes.INTEGER) {
                    int conditionValue = Integer.parseInt(condition.getValue());
                    stopFactorCheck = new StopFactorCheck(scoringId, s, String.valueOf(count));
                    if (count > conditionValue) {
                        stopFactorCheck.setResult(expectedStatus);
                    }
                } else if (condition.getValueType() == StopFactorValueTypes.DOUBLE) {
                    double conditionValue = Double.parseDouble(condition.getValue());
                    stopFactorCheck = new StopFactorCheck(scoringId, s, String.valueOf(count));
                    if (count > conditionValue) {
                        stopFactorCheck.setResult(expectedStatus);
                    }
                }
            }
            return stopFactorCheck;
        }

        private double getVariableFunction(long stopFactorId, StopFactorIntegerValueDto dto) {
            return switch ((int) stopFactorId) {
                case 1, 13, 14, 15, 16, 19 -> 0;
                case 2 -> dto.numberOfActiveDisputes();
                case 3 -> dto.numberOfUnjustifiedDisputesInPast();
                case 4 -> dto.numberOfRestructuringsLast12Months();
                case 5 -> dto.countOfChangesObLast12Month();
                case 6 -> dto.numberOfRestructuringsFrom12To24Months();
                case 7 -> dto.countOfChangesObFrom13To24Months();
                case 8 -> dto.creditInquiriesLast7Days();
                case 9 -> dto.currentNumberOfDaysOverdue();
                case 10 -> dto.currentAmountOfOverdue();
                case 11 -> dto.totalNumberOfNewOpenLoansInTheLast30Days();
                case 12 -> dto.totalOpenCreditContracts();
                case 17 -> dto.overdue().countWherePeriodFrom7To12MonthsAndOverdueFrom1To10();
                case 18 -> dto.overdue().countWherePeriodFrom7To12MonthsAndOverdueFrom11To20();
                case 20 -> dto.overdue().countWherePeriodFrom13To24MonthsAndOverdueFrom1To10();
                case 21 -> dto.overdue().countWherePeriodFrom13To24MonthsAndOverdueFrom11To20();
                case 22 -> dto.overdue().countWherePeriodFrom13To24MonthsAndOverdueFrom21To30();
                case 23 -> dto.overdue().countWherePeriodFrom13To24MonthsAndOverdueFrom31();
                case 24 -> dto.overdue().countWherePeriodFrom25To36MonthsAndOverdueFrom1To60();
                case 25 -> dto.overdue().countWherePeriodFrom25To36MonthsAndOverdueFrom61To90();
                case 26 -> dto.overdue().countWherePeriodFrom25To36MonthsAndOverdueFrom91();
                case 27 -> dto.overdue().countWherePeriodFrom1To3MonthsAndOverdueFrom1To15();
                case 28 -> dto.overdue().countWherePeriodFrom1To3MonthsAndOverdueFrom16To30();
                case 29 -> dto.overdue().countWherePeriodFrom1To3MonthsAndOverdueFrom31();
                case 30 -> dto.overdue().countWherePeriodFrom4To6MonthsAndOverdueFrom1To15();
                case 31 -> dto.overdue().countWherePeriodFrom4To6MonthsAndOverdueFrom16To30();
                case 32 -> dto.overdue().countWherePeriodFrom4To6MonthsAndOverdueFrom31();
                case 33 -> dto.overdue().countWherePeriodFrom7To12MonthsAndOverdueFrom21To30();
                case 34 -> dto.overdue().countWherePeriodFrom7To12MonthsAndOverdueFrom31();
                default -> throw new IllegalStateException("Unexpected value: " + (int) stopFactorId);
            };
        }

        private StopFactorIntegerValueDto initializeStopFactorIntegerValueDto(CustomReport customReport, IndividualReport individualReport, ConsolidationDto consolidationDto, TotalDebtFinalResponse totalDebt, LocalDate requestDate) {
            if (customReport != null && individualReport != null && consolidationDto != null) {
                CountOfChanges countOfChangesOb = getCountOfChangesOb(totalDebt.getCredits(), requestDate);
                var overdue = kibOverdueCalculator.getOverdueByPeriodAndCount(customReport, requestDate.atStartOfDay());
                return new StopFactorIntegerValueDto(
                        individualReport.generalInfoDto().numberOfActiveDisputes(),
                        individualReport.generalInfoDto().numberOfUnjustifiedDisputesInPast(),
                        getSumOfParamsWhereCustomerIsMainDebtorOrGuarantor(individualReport, LoanDto::getNumberOfRestructuringsLast12Months),
                        countOfChangesOb.countOfChangesObLast12Month(),
                        getSumOfParamsWhereCustomerIsMainDebtorOrGuarantor(individualReport, LoanDto::getNumberOfRestructuringsFrom12To24Months),
                        countOfChangesOb.countOfChangesObFrom13To24Months(),
                        individualReport.generalInfoDto().creditInquiriesLast7Days(),
                        Math.max(getMaxOfParamsWhereCustomerIsMainDebtorOrGuarantor(individualReport, LoanDto::getDaysDelay), totalDebt.getCredits().stream().mapToLong(TotalDebtReportData::getCurrentOverdueDaysCount).max().orElse(0)),
                        // Changed on purpose, see the class comment: the legacy did not add the guaranteed loans
                        consolidationDto.totalOverduePrincipalOrInterestOnAllOpenCreditContracts().doubleValue()
                                + guaranteedOverdueAmount(individualReport) +
                                (totalDebt.getCredits().stream().map(credit -> Optional.ofNullable(credit.getCurrentOverdueSum()).orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add)).doubleValue(),
                        consolidationDto.totalNumberOfNewOpenLoansInTheLast30Days(),
                        consolidationDto.totalOpenCreditContracts(),
                        overdue
                );
            }

            // Changed on purpose, see the class comment: the legacy returned 0 OB changes here
            CountOfChanges countOfChangesOb = getCountOfChangesOb(totalDebt.getCredits(), requestDate);
            return new StopFactorIntegerValueDto(0, 0, 0,
                    countOfChangesOb.countOfChangesObLast12Month(),
                    0,
                    countOfChangesOb.countOfChangesObFrom13To24Months(),
                    0,
                    totalDebt.getCredits().stream().mapToLong(TotalDebtReportData::getCurrentOverdueDaysCount).max().orElse(0),
                    (totalDebt.getCredits().stream().map(credit -> Optional.ofNullable(credit.getCurrentOverdueSum()).orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add)).doubleValue(),
                    0, 0, OverdueForStopFactor.NONE
            );
        }

        /** Added on purpose, see the class comment: overdue of the open loans the client guarantees. */
        private static double guaranteedOverdueAmount(IndividualReport individualReport) {
            return individualReport.loanDtoList().stream()
                    .filter(loan -> loan.getParticipationType() == RoleOfClient.Guarantor)
                    .filter(loan -> loan.getPhaseOfLoan() == LoanPhase.Open)
                    .filter(loan -> loan.getDelayAmount() > 0 || loan.getOverdueInterest() > 0)
                    .mapToDouble(loan -> loan.getDelayAmount() + loan.getOverdueInterest())
                    .sum();
        }

        private static @NotNull List<LoanStatus> getLoanStatuses(String[] array) {
            List<LoanStatus> loanStatusList = new ArrayList<>();
            for (String a : array) {
                loanStatusList.add(LoanStatus.valueOf(a));
            }
            return loanStatusList;
        }

        private static StopFactorResult checkLoanNotValidStatuses(IndividualReport individualReport, StopFactorStatus expectedStatus, List<LoanStatus> loanStatusList) {
            if (individualReport != null) {
                for (LoanDto loan : individualReport.loanDtoList()) {
                    if (
                            loan.getParticipationType() == RoleOfClient.MainDebtor ||
                                    loan.getParticipationType() == RoleOfClient.Guarantor
                    ) {
                        if (loanStatusList.contains(loan.getLoanStatus())) {
                            return new StopFactorResult(expectedStatus, String.valueOf(loan.getLoanStatus()));
                        }
                    }
                }
            }
            return new StopFactorResult(APPROVED, "Нет совпадений");
        }

        private int getSumOfParamsWhereCustomerIsMainDebtorOrGuarantor(IndividualReport individualReport, ToIntFunction<LoanDto> variableFunction) {
            int count = 0;
            for (LoanDto l : individualReport.loanDtoList()) {
                int variable = variableFunction.applyAsInt(l);
                if (l.getParticipationType() == RoleOfClient.MainDebtor || l.getParticipationType() == RoleOfClient.Guarantor) {
                    count += variable;
                }
            }
            return count;
        }

        private int getMaxOfParamsWhereCustomerIsMainDebtorOrGuarantor(IndividualReport individualReport, ToIntFunction<LoanDto> variableFunction) {
            int max = 0;
            for (LoanDto l : individualReport.loanDtoList()) {
                int variable = variableFunction.applyAsInt(l);
                if (l.getParticipationType() == RoleOfClient.MainDebtor || l.getParticipationType() == RoleOfClient.Guarantor) {
                    max = Math.max(max, variable);
                }
            }
            return max;
        }

        private CountOfChanges getCountOfChangesOb(List<TotalDebtReportData> credits, LocalDate requestDate) {
            int countOfChangesObLast12Month = 0;
            int countOfChangesObFrom13To24Months = 0;

            for (TotalDebtReportData item : credits) {
                if (item.getLastChangeDate() == null) {
                    continue;
                }

                if (MONTHS.between(item.getLastChangeDate(), requestDate) <= 12) {
                    countOfChangesObLast12Month++;
                }
                if (MONTHS.between(item.getLastChangeDate(), requestDate) >= 13 && MONTHS.between(item.getLastChangeDate(), requestDate) <= 24) {
                    countOfChangesObFrom13To24Months++;
                }
            }

            return new CountOfChanges(countOfChangesObLast12Month, countOfChangesObFrom13To24Months);
        }
    }

    private record CountOfChanges(int countOfChangesObLast12Month, int countOfChangesObFrom13To24Months) {
    }

    /** The legacy StopFactorResult, with the getters the legacy code reads. */
    private record StopFactorResult(StopFactorStatus status, String value) {

        StopFactorStatus getStatus() {
            return status;
        }

        String getValue() {
            return value;
        }
    }
}
