package kg.bakai.stopfactor;

import kg.bakai.spr.core.kib.KibService;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.GeneralInfoDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.kib.dto.OverdueForStopFactor;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.service.StopFactorService;
import kg.bakai.stopfactor.service.impl.StopFactorServiceImpl;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCheck;
import kg.bakai.stopfactor.entity.StopFactorCondition;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader;
import kg.bakai.stopfactor.report.LegacyOverdueCounters;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.ToIntFunction;

import static java.time.temporal.ChronoUnit.MONTHS;
import static kg.bakai.stopfactor.enums.StopFactorStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Runs the legacy SPR algorithm (a copy of StopFactorLegacyServiceImpl) and the new StopFactorServiceImpl
 * on the same KIB/OB reports against the migrated database (stop factors 1..34 from changelog-1.2, metrics and
 * params from changelog-1.3, grouped by changelog-1.4) and checks that every stop factor gets the same result and
 * checked value, and the application the same decision. Also checks scorings made before and after a stop factor
 * change.
 * <p>
 * The legacy algorithm only knows ids 1..34, so every test first brings the stop factors back to the state of
 * changelog 1.3 ({@link #rewindToChangelog13()}) and no overdue overdue months can be built, so the overdue stop
 * factors read the legacy counters. Every test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Transactional
class LegacyStopFactorParityTest {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 3);

    @MockitoBean
    private KibService kibService;
    @MockitoBean
    private KibOverdueHistoryReader overdueHistoryReader;

    @Autowired
    private StopFactorService stopFactorService;
    @Autowired
    private StopFactorConsoleService stopFactorConsoleService;
    @Autowired
    private StopFactorGroupRepository groupRepository;
    @Autowired
    private StopFactorCheckRepository stopFactorCheckRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OverdueForStopFactor kibOverdue = overdue(new int[17]);
    private long scoringId = -1_000_000;

    @BeforeEach
    void setUp() {
        rewindToChangelog13();
        // the 34 legacy stop factors are the stop factors 1..34 of 10 groups (#4: restructurings, #13: overdue history)
        List<StopFactorGroup> all = groupRepository.findAll();
        assertThat(all).extracting(StopFactorGroup::getId).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 8L, 9L, 10L, 11L, 12L, 13L);
        assertThat(all).allMatch(g -> g.getVersionNumber() == 1
                && g.getFactors().stream().allMatch(f -> f.getSegment() == null));
        assertThat(all.stream().flatMap(g -> g.getFactors().stream()).map(StopFactor::getId).toList())
                .containsExactlyInAnyOrderElementsOf(java.util.stream.LongStream.rangeClosed(1, 34).boxed().toList());
        when(kibService.getOverdueByPeriodAndCount(any(), any())).thenAnswer(invocation -> kibOverdue);
        when(overdueHistoryReader.read(any(), any())).thenReturn(null);
    }

    /**
     * Removes every version created after changelog 1.3 (changelog 1.4, console edits) and makes ids 1..34 current
     * again. Rolled back with the test.
     */
    private void rewindToChangelog13() {
        String newerFactors = "SELECT id FROM stop_factors.stop_factor WHERE group_id > 34";
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_condition WHERE stop_factor_id IN (" + newerFactors + ")");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_check WHERE stop_factor_id IN (" + newerFactors + ")");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_product WHERE stop_factor_id IN (" + newerFactors + ")");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor WHERE group_id > 34");
        jdbcTemplate.update("DELETE FROM stop_factors.stop_factor_group WHERE id > 34");
        jdbcTemplate.update("UPDATE stop_factors.stop_factor_group SET valid_to = NULL WHERE id <= 34");
    }

    // ------------------------------------------------------------------------------------------------
    // Same results as the legacy algorithm
    // ------------------------------------------------------------------------------------------------

    /**
     * Every stop factor of a legacy overdue group that reads a KIB counter has the params of that counter's range,
     * as its production name says (#27 "1-15 days" reads the KIB counter of months 1..3, 1-15 days).
     */
    @Test
    void legacyOverdueStopFactorsMeasureTheKibCounterTheyRead() {
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
                SELECT f.id, f.name,
                       (f.params->>'periodFromMonth')::int AS period_from, (f.params->>'periodToMonth')::int AS period_to,
                       (f.params->>'minOverdueDays')::int AS min_days, (f.params->>'maxOverdueDays')::int AS max_days
                FROM stop_factors.stop_factor f
                WHERE f.id BETWEEN 17 AND 34 AND f.id <> 19""");

        assertThat(versions).hasSize(17);
        for (Map<String, Object> v : versions) {
            assertThat(LegacyOverdueCounters.NONE.count((int) v.get("period_from"), (int) v.get("period_to"),
                    (int) v.get("min_days"), (Integer) v.get("max_days")))
                    .as("legacy stop factor %s", v.get("id"))
                    .isPresent();
        }
        Map<String, Object> stopFactor27 = versions.stream().filter(v -> v.get("id").equals(27L)).findFirst().orElseThrow();
        assertThat(stopFactor27).containsEntry("min_days", 1).containsEntry("max_days", 15);
        assertThat((String) stopFactor27.get("name")).endsWith("с 1 го по 3 месяц, где максимальная просрочка >= 1 и <= 15");
    }

    @Test
    void everyLegacyStopFactorReadsItsOwnValue() {
        // every value is different: a stop factor reading the wrong value would show another number
        kibOverdue = overdue(new int[]{101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117});
        Reports reports = new Reports(
                new GeneralInfoDto(1, 2, 7),
                List.of(new LoanDto(RoleOfClient.MainDebtor, LoanStatus.Open, 3, 5, 9)),
                new ConsolidationDto(new BigDecimal("9.5"), 10, 11),
                List.of(new TotalDebtReportData(4, new BigDecimal("0.25"), REQUEST_DATE.minusMonths(2)),
                        new TotalDebtReportData(0, null, REQUEST_DATE.minusMonths(14)),
                        new TotalDebtReportData(0, null, REQUEST_DATE.minusMonths(20))));

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
            kibOverdue = randomOverdue(random, percent);
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

        // the condition is changed in the console: version 2 of group 9 rejects only above 5 days
        StopFactorResponse current = stopFactorConsoleService.get(9);
        StopFactorDto factor = current.factors().getFirst();
        stopFactorConsoleService.update(9, new StopFactorRequest(current.description(), current.orderNumber(), current.active(),
                List.of(factor.withConditions(List.of(new ConditionDto(null, REJECTED, StopFactorValueTypes.INTEGER, "5")))),
                "Порог изменен"));
        StopFactorGroup version2 = groupRepository.findCurrent(9).orElseThrow();
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
    void stopFactorAddedInTheConsoleIsChecked() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest("Добавлен через консоль", null, true,
                List.of(new StopFactorDto(2L, Map.of(), List.of())), null));
        StopFactorGroup added = groupRepository.findCurrent(created.groupId()).orElseThrow();
        stopFactorConsoleService.saveCondition(added.getId(), created.factors().getFirst().id(),
                new ConditionDto(null, UNDERWRITER, StopFactorValueTypes.INTEGER, "1"), "Условие добавлено");

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
        jdbcTemplate.update("INSERT INTO stop_factors.stop_factor_product (stop_factor_id, product_id) "
                + "SELECT id, -2 FROM stop_factors.stop_factor WHERE group_id = 2");
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
     * Known difference: legacy returned a hardcoded 0 for 13, 14, 15, 16, 19 (inactive since 2024). Their periods
     * have no legacy counter, so if someone activates them while no overdue overdue months can be built, they go to
     * the underwriter instead of always passing.
     */
    @Test
    void inactiveLegacyStopFactorsWithoutAValueGoToUnderwriterIfActivated() {
        // #13..#16, #19 are inactive stop factors of the overdue history group #13 (version 1 after the rewind)
        List<Long> ids = List.of(13L, 14L, 15L, 16L, 19L);
        groupRepository.findCurrent(13).orElseThrow().getFactors().stream()
                .filter(factor -> ids.contains(factor.getId()))
                .forEach(factor -> factor.setActive(true));
        groupRepository.flush();

        long scoring = nextScoringId();
        check(scoring, new Reports(new GeneralInfoDto(0, 0, 0), List.of(), new ConsolidationDto(BigDecimal.ZERO, 0, 0), List.of()));

        assertThat(findChecks(scoring))
                .filteredOn(p -> ids.contains(p.getFactorId()))
                .hasSize(5)
                .allSatisfy(p -> {
                    assertThat(p.getStopFactorId()).isEqualTo(13L);
                    assertThat(p.getCheckResult()).isEqualTo(UNDERWRITER);
                    assertThat(p.getCheckedValue()).isEqualTo(StopFactorServiceImpl.NO_DATA);
                });
    }

    @Test
    void legacyStopFactorsOfOneMetricAreStopFactorsOfOneGroup() {
        StopFactorGroup restructurings = groupRepository.findCurrent(4).orElseThrow();
        assertThat(restructurings.getName()).isEqualTo("Реструктуризации");
        assertThat(restructurings.getFactors()).extracting(StopFactor::getId).containsExactly(4L, 5L, 6L, 7L);

        StopFactorGroup overdue = groupRepository.findCurrent(13).orElseThrow();
        assertThat(overdue.getName()).isEqualTo("Непрерывная просроченная задолженность");
        assertThat(overdue.getFactors()).hasSize(22);
        assertThat(overdue.getFactors()).filteredOn(StopFactor::isActive).extracting(StopFactor::getId)
                .containsExactly(27L, 28L, 29L, 30L, 31L, 32L, 17L, 18L, 33L, 34L, 20L, 21L, 22L, 23L, 24L, 25L, 26L);
        for (long merged = 5; merged <= 34; merged++) {
            if (merged != 8 && merged != 9 && merged != 10 && merged != 11 && merged != 12 && merged != 13) {
                assertThat(groupRepository.findById(merged)).as("group " + merged).isEmpty();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------

    private record Reports(GeneralInfoDto generalInfo, List<LoanDto> loans, ConsolidationDto consolidation,
                           List<TotalDebtReportData> obCredits) {

        CustomReport customReport() {
            return generalInfo != null ? new CustomReport() : null;
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
                .extracting(p -> p.getFactorName() + " | " + p.getCheckResult() + " | " + p.getCheckedValue())
                .containsExactlyElementsOf(legacy.checks().stream()
                        .map(c -> c.getStopFactor().getName() + " | " + c.getResult() + " | " + c.getCheckedValue())
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

    private static OverdueForStopFactor overdue(int[] o) {
        return new OverdueForStopFactor(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], o[8],
                o[9], o[10], o[11], o[12], o[13], o[14], o[15], o[16]);
    }

    // 0 in most cases, so that approved and underwriter outcomes happen as well as rejected ones
    private static int rarely(Random random, int percent, int max) {
        return random.nextInt(100) >= percent ? 0 : 1 + random.nextInt(max);
    }

    private static OverdueForStopFactor randomOverdue(Random random, int percent) {
        int[] o = new int[17];
        for (int i = 0; i < o.length; i++) {
            o[i] = rarely(random, percent, 12);
        }
        return overdue(o);
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
            loans.add(new LoanDto(
                    RoleOfClient.values()[random.nextInt(RoleOfClient.values().length)],
                    random.nextInt(100) >= percent ? LoanStatus.Open : LoanStatus.values()[random.nextInt(LoanStatus.values().length)],
                    rarely(random, percent, 1),
                    rarely(random, percent, 1),
                    rarely(random, percent, 30)));
        }
        return new Reports(
                new GeneralInfoDto(rarely(random, percent, 1), rarely(random, percent, 1), rarely(random, percent, 15) * 2),
                loans,
                new ConsolidationDto(random.nextInt(100) >= percent ? BigDecimal.ZERO : new BigDecimal(random.nextInt(100_000)).movePointLeft(2),
                        rarely(random, percent, 8), rarely(random, percent, 15)),
                obCredits);
    }

    private record LegacyResult(StopFactorDecision decision, List<StopFactorCheck> checks) {
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
        StopFactorIntegerValueDto(double currentNumberOfDaysOverdue, double currentAmountOfOverdue) {
            this(0, 0, 0, 0, 0, 0, 0, currentNumberOfDaysOverdue, currentAmountOfOverdue, 0, 0, OverdueForStopFactor.NONE);
        }
    }

    /**
     * Copy of StopFactorLegacyServiceImpl, the logic is not changed. The only difference: check() returns the checks
     * instead of saving them, so they can be compared with the new service.
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

            var stopFactors = groupRepository.findCurrentActiveWithFactorsAndMetric();
            List<StopFactorCheck> stopFactorChecks = new ArrayList<>();

            // a legacy stop factor is an active stop factor of a group now; its id is the legacy id (changelog 1.3 and 1.4.1)
            for (var s : stopFactors.stream().flatMap(g -> g.getFactors().stream()).filter(StopFactor::isActive).toList()) {
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

        private StopFactorCheck getStopFactorCheck(long scoringId, IndividualReport individualReport, StopFactor s, StopFactorCheck stopFactorCheck, double count, StopFactorStatus expectedStatus) {
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
                var overdue = kibService.getOverdueByPeriodAndCount(customReport, requestDate.atStartOfDay());
                return new StopFactorIntegerValueDto(
                        individualReport.generalInfoDto().numberOfActiveDisputes(),
                        individualReport.generalInfoDto().numberOfUnjustifiedDisputesInPast(),
                        getSumOfParamsWhereCustomerIsMainDebtorOrGuarantor(individualReport, LoanDto::getNumberOfRestructuringsLast12Months),
                        countOfChangesOb.countOfChangesObLast12Month(),
                        getSumOfParamsWhereCustomerIsMainDebtorOrGuarantor(individualReport, LoanDto::getNumberOfRestructuringsFrom12To24Months),
                        countOfChangesOb.countOfChangesObFrom13To24Months(),
                        individualReport.generalInfoDto().creditInquiriesLast7Days(),
                        Math.max(getMaxOfParamsWhereCustomerIsMainDebtorOrGuarantor(individualReport, LoanDto::getDaysDelay), totalDebt.getCredits().stream().mapToLong(TotalDebtReportData::getCurrentOverdueDaysCount).max().orElse(0)),
                        consolidationDto.totalOverduePrincipalOrInterestOnAllOpenCreditContracts().doubleValue() +
                                (totalDebt.getCredits().stream().map(credit -> Optional.ofNullable(credit.getCurrentOverdueSum()).orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add)).doubleValue(),
                        consolidationDto.totalNumberOfNewOpenLoansInTheLast30Days(),
                        consolidationDto.totalOpenCreditContracts(),
                        overdue
                );
            }

            return new StopFactorIntegerValueDto(
                    totalDebt.getCredits().stream().mapToLong(TotalDebtReportData::getCurrentOverdueDaysCount).max().orElse(0),
                    (totalDebt.getCredits().stream().map(credit -> Optional.ofNullable(credit.getCurrentOverdueSum()).orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add)).doubleValue()
            );
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
