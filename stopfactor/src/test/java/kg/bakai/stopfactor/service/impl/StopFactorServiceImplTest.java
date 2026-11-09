package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
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
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.ParamSpec;
import kg.bakai.stopfactor.config.CalculatorConfig;
import kg.bakai.stopfactor.calculator.KibRefusalStatusesCalculator;
import kg.bakai.stopfactor.calculator.OverdueBucketCountCalculator;
import kg.bakai.stopfactor.calculator.RestructuringCountCalculator;
import kg.bakai.stopfactor.condition.ConditionEvaluator;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.RuleDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCheck;
import kg.bakai.stopfactor.entity.StopFactorRule;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.report.fact.CheckContextFactory;
import kg.bakai.stopfactor.report.provider.CreditHistoryProvider;
import kg.bakai.stopfactor.report.provider.LegacyCountersProvider;
import kg.bakai.stopfactor.report.provider.LoanStatusesProvider;
import kg.bakai.stopfactor.report.provider.ReportValuesProvider;
import kg.bakai.stopfactor.report.CreditHistoryEvents;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader.OverdueHistory;
import kg.bakai.stopfactor.report.KibOverdueHistoryReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static kg.bakai.spr.core.kib.dto.enums.LoanStatus.*;
import static kg.bakai.spr.core.kib.dto.enums.RoleOfClient.*;
import static kg.bakai.stopfactor.enums.CalculatorType.*;
import static kg.bakai.stopfactor.enums.StopFactorStatus.*;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests of the stop factor check, no database. Stop factors are built the way changelog 1.3 left them:
 * legacy ids 1..34 are version 1 with a metric and params. SPR returns no overdue overdue months (null) unless a test
 * sets them, so overdue stop factors read the legacy counters.
 */
class StopFactorServiceImplTest {

    private static final long SCORING_ID = 1L;
    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 3);

    private final StopFactorRepository stopFactorRepository = mock(StopFactorRepository.class);
    private final StopFactorCheckRepository checkRepository = mock(StopFactorCheckRepository.class);
    private final StopFactorMetricRepository metricRepository = mock(StopFactorMetricRepository.class);
    private final StopFactorConsoleService consoleService = mock(StopFactorConsoleService.class);
    private final KibOverdueHistoryReader overdueHistoryReader = mock(KibOverdueHistoryReader.class);

    private final List<StopFactor> activeStopFactors = new ArrayList<>();
    /** The overdue months the KIB payment calendars show: none unless set. */
    private List<CreditHistoryEvents.OverdueMonth> kibOverdueMonths = List.of();

    private StopFactorServiceImpl service;
    private CalculatorRegistry registry;

    @BeforeEach
    void setUp() {
        // the calculators of CalculatorConfig + the @Component calculators, as in the application
        CalculatorConfig config = new CalculatorConfig();
        registry = new CalculatorRegistry(List.of(
                config.kibActiveDisputesCalculator(),
                config.kibUnjustifiedDisputesCalculator(),
                config.currentOverdueDaysCalculator(),
                config.currentOverdueAmountCalculator(),
                config.openCreditsCountCalculator(),
                config.bureauRequestsCountCalculator(),
                config.newCreditsCountCalculator(),
                new KibRefusalStatusesCalculator(),
                new OverdueBucketCountCalculator(),
                new RestructuringCountCalculator()
        ));
        service = new StopFactorServiceImpl(stopFactorRepository, checkRepository, metricRepository,
                consoleService, new CheckContextFactory(List.of(new ReportValuesProvider(), new LegacyCountersProvider(),
                new CreditHistoryProvider(overdueHistoryReader), new LoanStatusesProvider())), registry);

        when(stopFactorRepository.findCurrentActiveWithMetric()).thenReturn(activeStopFactors);
        when(stopFactorRepository.findProductByScoringId(anyLong())).thenReturn(Optional.empty());
        when(overdueHistoryReader.read(any(), any())).thenAnswer(invocation -> new OverdueHistory(kibOverdueMonths));
    }

    @Nested
    @DisplayName("Legacy rules (same as StopFactorLegacyServiceImpl)")
    class LegacyRules {

        @Test
        void approvedWithNullCodeWhenNothingFires() {
            StopFactor disputes = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(APPROVED, null));
        }

        @Test
        void numberConditionFiresOnlyWhenValueIsGreater() {
            StopFactor bureau = stopFactor(8, BUREAU_REQUESTS_COUNT, Map.of("days", 7));
            condition(bureau, REJECTED, INTEGER, "14");

            assertThat(check(new Application().bureauRequests(14)).status()).isEqualTo(APPROVED);
            assertThat(check(new Application().bureauRequests(15)).status()).isEqualTo(REJECTED);
        }

        @Test
        void underwriterConditionIsCheckedOnlyWhenNotRejected() {
            // legacy stop factor 27: REJECTED > 9, UNDERWRITER > 6
            StopFactor bucket = stopFactor(27, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 15));
            condition(bucket, REJECTED, INTEGER, "9");
            condition(bucket, UNDERWRITER, INTEGER, "6");

            kibOverdueMonths = Collections.nCopies(6, new CreditHistoryEvents.OverdueMonth(2, 5));
            assertThat(check(new Application()).status()).isEqualTo(APPROVED);
            kibOverdueMonths = Collections.nCopies(7, new CreditHistoryEvents.OverdueMonth(2, 5));
            assertThat(check(new Application()).status()).isEqualTo(UNDERWRITER);
            kibOverdueMonths = Collections.nCopies(10, new CreditHistoryEvents.OverdueMonth(2, 5));
            assertThat(check(new Application()).status()).isEqualTo(REJECTED);
        }

        @Test
        void rejectedWinsOverUnderwriterOfAnotherStopFactor() {
            StopFactor disputes = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            StopFactor openCredits = stopFactor(12, OPEN_CREDITS_COUNT, Map.of());
            condition(openCredits, UNDERWRITER, INTEGER, "0");

            StopFactorDecision decision = check(new Application().activeDisputes(1).consolidation("0", 0, 1));

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getResult).containsExactly(REJECTED, UNDERWRITER);
        }

        @Test
        void anyUnderwriterSendsToUnderwriterWithNullCode() {
            StopFactor openCredits = stopFactor(12, OPEN_CREDITS_COUNT, Map.of());
            condition(openCredits, UNDERWRITER, INTEGER, "8");

            assertThat(check(new Application().consolidation("0", 0, 9)))
                    .isEqualTo(new StopFactorDecision(UNDERWRITER, null));
        }

        @Test
        void currentOverdueDaysGive451EvenTogetherWithOtherRejectedStopFactors() {
            StopFactor disputes = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            StopFactor overdueDays = stopFactor(9, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDays, REJECTED, INTEGER, "0");

            assertThat(check(new Application().activeDisputes(1).obCredit(3, null, null)))
                    .isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
        }

        @Test
        void currentOverdueAmountGives451() {
            StopFactor overdueAmount = stopFactor(10, CURRENT_OVERDUE_AMOUNT, Map.of());
            condition(overdueAmount, REJECTED, DOUBLE, "0");

            assertThat(check(new Application().obCredit(0, "0.01", null)))
                    .isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
        }

        @Test
        void currentOverdueAmountIncludesTheOpenLoansTheClientGuarantees() {
            StopFactor overdueAmount = stopFactor(10, CURRENT_OVERDUE_AMOUNT, Map.of());
            condition(overdueAmount, REJECTED, DOUBLE, "0");

            StopFactorDecision decision = check(new Application()
                    .loan(LoanDto.builder()
                            .participationType(Guarantor)
                            .loanStatus(Open)
                            .phaseOfLoan(LoanPhase.Open)
                            .delayAmount(1200.50)
                            .overdueInterest(30)
                            .build()));

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("1230.5");
        }

        @Test
        void currentOverdueAmountAddsTheGuaranteedLoansToTheConsolidationAndOb() {
            stopFactor(10, CURRENT_OVERDUE_AMOUNT, Map.of());

            check(new Application()
                    .consolidation("100.00", 0, 2)  // main debtor loans
                    // already in the consolidation
                    .loan(LoanDto.builder()
                            .participationType(MainDebtor)
                            .loanStatus(Open)
                            .phaseOfLoan(LoanPhase.Open)
                            .delayAmount(100)
                            .build())
                    .loan(LoanDto.builder()
                            .participationType(Guarantor)
                            .loanStatus(Open)
                            .phaseOfLoan(LoanPhase.Open)
                            .delayAmount(20)
                            .overdueInterest(5)
                            .build())
                    // closed: not counted
                    .loan(LoanDto.builder()
                            .participationType(Guarantor)
                            .loanStatus(FullyPaid)
                            .phaseOfLoan(LoanPhase.Closed)
                            .delayAmount(999)
                            .build())
                    // not the client's own loan
                    .loan(LoanDto.builder()
                            .participationType(CoDebtor)
                            .loanStatus(Open)
                            .phaseOfLoan(LoanPhase.Open)
                            .delayAmount(999)
                            .build())
                    .obCredit(0, "1.50", null));

            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("126.5");
        }

        @Test
        void otherRejectedStopFactorsKeepNullCode() {
            StopFactor disputes = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");

            assertThat(check(new Application().activeDisputes(1))).isEqualTo(new StopFactorDecision(REJECTED, null));
        }

        @Test
        void refusalStatusOfAMainDebtorOrGuarantorLoanRejects() {
            kibStatusesStopFactor();

            StopFactorDecision decision = check(new Application()
                    .loan(MainDebtor, Open)
                    .loan(Guarantor, SoldToThirdParty)
                    .loan(MainDebtor, WrittenOff));

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("SoldToThirdParty");
        }

        @Test
        void refusalStatusOfACoDebtorLoanIsIgnored() {
            kibStatusesStopFactor();

            assertThat(check(new Application().loan(CoDebtor, WrittenOff)).status()).isEqualTo(APPROVED);
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo(ConditionEvaluator.NO_MATCHES);
        }

        @Test
        void underwriterStatusSendsToUnderwriter() {
            kibStatusesStopFactor();

            assertThat(check(new Application().loan(MainDebtor, TransferredToOtherReportingInst)).status()).isEqualTo(UNDERWRITER);
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("TransferredToOtherReportingInst");
        }

        @Test
        void withoutKibReportOnlyObCurrentOverdueIsChecked() {
            kibStatusesStopFactor();
            StopFactor disputes = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            stopFactor(9, CURRENT_OVERDUE_DAYS, Map.of());

            StopFactorDecision decision = check(new Application().withoutKib().obCredit(4, null, null));

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue)
                    .containsExactly(ConditionEvaluator.NO_MATCHES, "0.0", "4.0");
            verifyNoInteractions(overdueHistoryReader);
        }

        @Test
        void withoutKibReportAnObRestructuringOfMonths1To12Rejects() {
            StopFactor ob = stopFactor(5, RESTRUCTURING_COUNT, Map.of("source", "OB", "periodFromMonth", 1, "periodToMonth", 12));
            condition(ob, REJECTED, INTEGER, "0");

            StopFactorDecision decision = check(new Application().withoutKib().obCredit(0, null, REQUEST_DATE.minusMonths(3)));

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("1.0");
            verifyNoInteractions(overdueHistoryReader);
        }

        @Test
        void withoutKibReportAnObRestructuringOfMonths13To24GoesToTheUnderwriter() {
            StopFactor ob = stopFactor(7, RESTRUCTURING_COUNT, Map.of("source", "OB", "periodFromMonth", 13, "periodToMonth", 24));
            condition(ob, UNDERWRITER, INTEGER, "0");

            StopFactorDecision decision = check(new Application().withoutKib().obCredit(0, null, REQUEST_DATE.minusMonths(15)));

            assertThat(decision).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("1.0");
        }

        @Test
        void withoutKibReportTheKibRestructuringsAreZero() {
            StopFactor kib = stopFactor(4, RESTRUCTURING_COUNT, Map.of("source", "KIB", "periodFromMonth", 1, "periodToMonth", 12));
            condition(kib, REJECTED, INTEGER, "0");

            StopFactorDecision decision = check(new Application().withoutKib().obCredit(0, null, REQUEST_DATE.minusMonths(3)));

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("0.0");
        }

        @Test
        void valuesAreTakenFromTheReportsLikeLegacy() {
            stopFactor(4, RESTRUCTURING_COUNT, Map.of("source", "KIB", "periodFromMonth", 1, "periodToMonth", 12));
            stopFactor(5, RESTRUCTURING_COUNT, Map.of("source", "OB", "periodFromMonth", 1, "periodToMonth", 12));
            stopFactor(7, RESTRUCTURING_COUNT, Map.of("source", "OB", "periodFromMonth", 13, "periodToMonth", 24));
            stopFactor(9, CURRENT_OVERDUE_DAYS, Map.of());
            stopFactor(10, CURRENT_OVERDUE_AMOUNT, Map.of());

            check(new Application()
                    .loan(LoanDto.builder()
                            .participationType(MainDebtor)
                            .loanStatus(Open)
                            .numberOfRestructuringsLast12Months(2)
                            .daysDelay(5)
                            .phaseOfLoan(LoanPhase.Open)
                            .build())
                    .loan(LoanDto.builder()
                            .participationType(Guarantor)
                            .loanStatus(Open)
                            .numberOfRestructuringsLast12Months(1)
                            .daysDelay(12)
                            .phaseOfLoan(LoanPhase.Open)
                            .build())
                    // not the client's own loan
                    .loan(LoanDto.builder()
                            .participationType(CoDebtor)
                            .loanStatus(Open)
                            .numberOfRestructuringsLast12Months(10)
                            .daysDelay(99)
                            .phaseOfLoan(LoanPhase.Open)
                            .build())
                    .consolidation("1000", 0, 0)
                    .obCredit(7, "100.25", REQUEST_DATE.minusMonths(3))          // changed in months 1-12
                    .obCredit(0, null, REQUEST_DATE.minusMonths(15))             // changed in months 13-24
                    .obCredit(0, "50", null));

            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly(
                    "3.0",       // 4:  KIB restructurings of main debtor + guarantor loans
                    "1.0",       // 5:  OB changes in the last 12 months
                    "1.0",       // 7:  OB changes in months 13-24
                    "12.0",      // 9:  max(KIB days delay 12, OB overdue days 7)
                    "1150.25"    // 10: KIB overdue 1000 + OB overdue 100.25 + 50
            );
        }

        @Test
        void stopFactorWithoutConditionsApproves() {
            stopFactor(12, OPEN_CREDITS_COUNT, Map.of());

            assertThat(check(new Application().consolidation("0", 0, 100)).status()).isEqualTo(APPROVED);
        }

        @Test
        void everyActiveStopFactorIsSavedInOrderForTheScoring() {
            stopFactor(1, KIB_REFUSAL_STATUSES, Map.of());
            stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            stopFactor(3, KIB_UNJUSTIFIED_DISPUTES, Map.of());

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(1L, 2L, 3L);
            assertThat(savedChecks()).allMatch(c -> c.getScoringId() == SCORING_ID);
        }
    }

    @Nested
    @DisplayName("Versioned stop factors: versions, metrics, NO_DATA")
    class NewStructure {

        @Test
        void stopFactorEditedInConsoleIsCheckedByItsCurrentVersion() {
            // version 2 of legacy stop factor 9 has id 35: the legacy switch threw "Unexpected value: 35"
            StopFactor overdueDaysV2 = version(35, 9, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDaysV2, REJECTED, INTEGER, "5");

            StopFactorDecision decision = check(new Application().obCredit(6, null, null));

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
            StopFactorCheck check = savedChecks().getFirst();
            assertThat(check.getStopFactor().getId()).isEqualTo(35L);
            assertThat(check.getStopFactor().getFactorId()).isEqualTo(9L);
            assertThat(check.getCheckedValue()).isEqualTo("6.0");
        }

        @Test
        void conditionsAreTakenFromTheCurrentVersionNotFromVersionOne() {
            // version 1 (closed, not active any more) rejected any overdue, version 2 only above 5 days
            StopFactor overdueDaysV1 = stopFactor(9, CURRENT_OVERDUE_DAYS, Map.of());
            activeStopFactors.remove(overdueDaysV1);
            condition(overdueDaysV1, REJECTED, INTEGER, "0");
            StopFactor overdueDaysV2 = version(35, 9, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDaysV2, REJECTED, INTEGER, "5");

            assertThat(check(new Application().obCredit(3, null, null))).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(35L);
        }

        @Test
        void newStopFactorOfALegacyMetricGetsTheLegacyValue() {
            // created in the console, any id, no case in the legacy switch
            StopFactor newDisputes = stopFactor(100, KIB_ACTIVE_DISPUTES, Map.of());
            condition(newDisputes, UNDERWRITER, INTEGER, "1");

            assertThat(check(new Application().activeDisputes(2))).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("2.0");
        }

        @Test
        void periodWithoutALegacyValueGoesToUnderwriterAsNoData() {
            // SPR gives no restructuring events, and the legacy counters have months 1..12 and 13..24 only
            StopFactor restructurings = stopFactor(101, RESTRUCTURING_COUNT,
                    Map.of("source", "KIB", "periodFromMonth", 1, "periodToMonth", 6));
            condition(restructurings, REJECTED, INTEGER, "0");

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo(StopFactorServiceImpl.NO_DATA);
        }

        @Test
        void noDataDoesNotOverrideRejected() {
            StopFactor disputes = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            stopFactor(101, RESTRUCTURING_COUNT, Map.of("source", "KIB", "periodFromMonth", 1, "periodToMonth", 6));

            assertThat(check(new Application().activeDisputes(1))).isEqualTo(new StopFactorDecision(REJECTED, null));
        }

        @Test
        void statusSavedInTheConsoleWithoutSpacesWorks() {
            StopFactor kib = stopFactor(1, KIB_REFUSAL_STATUSES, Map.of());
            condition(kib, REJECTED, ARRAY, "WrittenOff,SoldToThirdParty");

            assertThat(check(new Application().loan(MainDebtor, SoldToThirdParty)).status()).isEqualTo(REJECTED);
        }

        @Test
        void unknownStatusInAConditionIsSkippedInsteadOfFailingTheScoring() {
            StopFactor kib = stopFactor(1, KIB_REFUSAL_STATUSES, Map.of());
            condition(kib, REJECTED, ARRAY, "NoSuchStatus, WrittenOff");

            assertThat(check(new Application().loan(MainDebtor, WrittenOff)).status()).isEqualTo(REJECTED);
        }

        @Test
        void stringConditionIsAOneStatusList() {
            StopFactor kib = stopFactor(1, KIB_REFUSAL_STATUSES, Map.of());
            condition(kib, REJECTED, STRING, "WrittenOff");

            assertThat(check(new Application().loan(Guarantor, WrittenOff)).status()).isEqualTo(REJECTED);
        }

        @Test
        void repeatedCheckReplacesPreviousResultsOfTheScoring() {
            stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());

            check(new Application());

            InOrder inOrder = inOrder(checkRepository);
            inOrder.verify(checkRepository).deleteByScoringId(SCORING_ID);
            inOrder.verify(checkRepository).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("Overdue months of the KIB payment calendars (KibOverdueHistoryReader)")
    class OverdueMonths {

        @Test
        void overdueMonthsAreCountedForAnyPeriodAndRange() {
            // Retail 2026, months 7..12, 6..10 days: UNDERWRITER > 5, REJECTED > 8 - no legacy counter for it
            StopFactor bucket = stopFactor(200, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 7, "periodToMonth", 12, "minOverdueDays", 6, "maxOverdueDays", 10));
            condition(bucket, REJECTED, INTEGER, "8");
            condition(bucket, UNDERWRITER, INTEGER, "5");

            List<CreditHistoryEvents.OverdueMonth> counted = List.of(
                    overdueMonth(7, 6), overdueMonth(12, 10), overdueMonth(9, 8), overdueMonth(8, 7), overdueMonth(10, 9), overdueMonth(11, 6));
            List<CreditHistoryEvents.OverdueMonth> notCounted = List.of(
                    overdueMonth(6, 8), overdueMonth(13, 8), overdueMonth(9, 5), overdueMonth(9, 11));

            kibOverdueMonths = concat(counted.subList(0, 5), notCounted);
            assertThat(check(new Application()).status()).isEqualTo(APPROVED);
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("5.0");

            kibOverdueMonths = concat(counted, notCounted);
            assertThat(check(new Application()).status()).isEqualTo(UNDERWRITER);
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("6.0");
        }

        @Test
        void legacyStopFactorsCountTheMonthsOfTheCalendars() {
            // legacy stop factor 27 (months 1-3, overdue 1-15) counts the months of the payment calendars as KIB did
            StopFactor bucket = stopFactor(27, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 15));
            condition(bucket, REJECTED, INTEGER, "0");
            kibOverdueMonths = List.of(overdueMonth(3, 15), overdueMonth(4, 15), overdueMonth(1, 16));

            assertThat(check(new Application()).status()).isEqualTo(REJECTED);
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("1.0");
            verify(overdueHistoryReader).read(any(CustomReport.class), eq(REQUEST_DATE.atStartOfDay()));
        }

        @Test
        void withoutKibReportEveryOverdueCountIsZero() {
            // no credit history: 0 like the legacy counters, also for a range without a legacy counter
            StopFactor bucket = stopFactor(201, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 11, "maxOverdueDays", 20));
            condition(bucket, REJECTED, INTEGER, "0");

            assertThat(check(new Application().withoutKib())).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("0.0");
        }

        @Test
        void aKibReportWithoutCalendarsHasNoOverdueInsteadOfNoData() {
            // the reader finds no payment calendars: no overdue, also for a range without a legacy counter
            StopFactor bucket = stopFactor(201, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 11, "maxOverdueDays", 20));
            condition(bucket, REJECTED, INTEGER, "0");
            kibOverdueMonths = List.of();

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("0.0");
        }

        private static CreditHistoryEvents.OverdueMonth overdueMonth(int monthsAgo, int maxOverdueDays) {
            return new CreditHistoryEvents.OverdueMonth(monthsAgo, maxOverdueDays);
        }

        private static List<CreditHistoryEvents.OverdueMonth> concat(List<CreditHistoryEvents.OverdueMonth> a,
                                                                       List<CreditHistoryEvents.OverdueMonth> b) {
            List<CreditHistoryEvents.OverdueMonth> all = new ArrayList<>(a);
            all.addAll(b);
            return all;
        }
    }

    /**
     * How the overdue history is counted, with the real {@link KibOverdueHistoryReader}: months the payment calendars
     * do not show had no overdue (analyst's decision of 09.11.2026); every calendar month with overdue is one case
     * (pinned until the analyst decides on counting overdue episodes).
     */
    @Nested
    class CurrentOverdueCounting {

        private StopFactorServiceImpl withRealReader;

        @BeforeEach
        void setUp() {
            withRealReader = new StopFactorServiceImpl(stopFactorRepository, checkRepository, metricRepository,
                    consoleService, new CheckContextFactory(List.of(new ReportValuesProvider(), new LegacyCountersProvider(),
                    new CreditHistoryProvider(new KibOverdueHistoryReader()), new LoanStatusesProvider())), registry);
        }

        /**
         * The calendar covers 24 months of a contract that started 40 months ago: months 25..36 had no overdue
         * (analyst's decision of 09.11.2026), so the 2026 bands of months 25..36 give 0, not NO_DATA.
         */
        @Test
        void aCalendarShorterThanTheContractShowsNoOverdueForMonths25To36() {
            StopFactor days31To60 = stopFactor(80, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 25, "periodToMonth", 36, "minOverdueDays", 31, "maxOverdueDays", 60));
            condition(days31To60, REJECTED, INTEGER, "0");
            StopFactor days61AndMore = stopFactor(81, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 25, "periodToMonth", 36, "minOverdueDays", 61));
            condition(days61AndMore, REJECTED, INTEGER, "0");
            StopFactor months1To3 = stopFactor(82, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10));
            condition(months1To3, REJECTED, INTEGER, "5");

            StopFactorDecision decision = withRealReader.check(SCORING_ID, customReport(contract(
                            REQUEST_DATE.minusMonths(40), calendarWithoutOverdue(24))),
                    new IndividualReport(new GeneralInfoDto(0, 0, 0), List.of()), new ConsolidationDto(BigDecimal.ZERO, 0, 0),
                    new TotalDebtFinalResponse(new ArrayList<>()), REQUEST_DATE);

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue, StopFactorCheck::getResult)
                    .containsExactly(tuple("0.0", APPROVED), tuple("0.0", APPROVED), tuple("0.0", APPROVED));
        }

        /**
         * 2b: every calendar month with MaxPastDueDays of 1 and more is one case. An overdue of 4 days at the end of
         * month 2 (PastDueDays = 4) that goes on to 8 days in month 1 is one overdue, but it counts twice: 3 contracts
         * give 6 cases of "months 1-3, 1-10 days", and the retail rule (6 and more - refusal) rejects.
         */
        @Test
        void anOverdueGoingOnIntoTheNextMonthCountsInBothMonths() {
            StopFactor retail = stopFactor(83, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10));
            condition(retail, REJECTED, INTEGER, "5");
            // 24 months of calendar from the start of the contract, so months 1..3 are complete
            List<CalendarItem> calendar = calendarWithoutOverdue(24);
            calendar.set(22, calendarItem(REQUEST_DATE.minusMonths(1), 4, 4));
            calendar.set(23, calendarItem(REQUEST_DATE, 0, 8));
            Contract contract = contract(REQUEST_DATE.minusMonths(23), calendar);

            StopFactorDecision decision = withRealReader.check(SCORING_ID, customReport(contract, contract, contract),
                    new IndividualReport(new GeneralInfoDto(0, 0, 0), List.of()), new ConsolidationDto(BigDecimal.ZERO, 0, 0),
                    new TotalDebtFinalResponse(new ArrayList<>()), REQUEST_DATE);

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("6.0");
        }

        private static CustomReport customReport(Contract... contracts) {
            return new CustomReport(new Contracts(new ContractList(List.of(contracts))));
        }

        private static Contract contract(LocalDate startDate, List<CalendarItem> calendar) {
            return new Contract(MainDebtor, startDate.atStartOfDay(), new PaymentCalendarList(calendar));
        }

        /** The last months of calendar, up to the month of the request, without overdue. */
        private static List<CalendarItem> calendarWithoutOverdue(int months) {
            List<CalendarItem> calendar = new ArrayList<>();
            for (int monthsAgo = months - 1; monthsAgo >= 0; monthsAgo--) {
                calendar.add(calendarItem(REQUEST_DATE.minusMonths(monthsAgo), 0, 0));
            }
            return calendar;
        }

        /** A calendar entry dated the last day of the month, as KIB sends it. */
        private static CalendarItem calendarItem(LocalDate month, int pastDueDays, int maxPastDueDays) {
            return new CalendarItem(month.withDayOfMonth(month.lengthOfMonth()).atStartOfDay(), pastDueDays, maxPastDueDays);
        }
    }

    @Nested
    @DisplayName("Products (public.scoring -> public.product)")
    class Products {

        @Test
        void scoringWithoutProductChecksAllStopFactorsLikeLegacy() {
            stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of()).getProductIds().add(5L);
            stopFactor(3, KIB_UNJUSTIFIED_DISPUTES, Map.of());

            check(new Application());

            assertThat(savedChecks()).hasSize(2).allMatch(c -> c.getProductId() == null);
        }

        @Test
        void stopFactorLimitedToAnotherProductIsSkipped() {
            ProductProjection product = productProjection(7L, RETAIL);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of()).getProductIds().add(5L);
            stopFactor(3, KIB_UNJUSTIFIED_DISPUTES, Map.of()).getProductIds().add(7L);
            stopFactor(4, KIB_ACTIVE_DISPUTES, Map.of());   // no products = every product

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L, 4L);
            assertThat(savedChecks()).allMatch(c -> c.getProductId() == 7L);
        }

        @Test
        void stopFactorOfAnotherSegmentIsSkipped() {
            ProductProjection product = productProjection(7L, MSB);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of()).setSegment(RETAIL);
            stopFactor(3, KIB_UNJUSTIFIED_DISPUTES, Map.of()).setSegment(MSB);
            stopFactor(4, KIB_ACTIVE_DISPUTES, Map.of());   // no segment = every segment

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L, 4L);
        }

        @Test
        void scoringWithoutProductGetsTheDefaultSegment() {
            stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of()).setSegment(RETAIL);
            stopFactor(3, KIB_UNJUSTIFIED_DISPUTES, Map.of()).setSegment(MSB);

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(2L);
        }

        @Test
        void segmentAndProductLimitsMustBothMatch() {
            ProductProjection product = productProjection(7L, MSB);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            StopFactor otherProduct = stopFactor(2, KIB_ACTIVE_DISPUTES, Map.of());
            otherProduct.setSegment(MSB);
            otherProduct.getProductIds().add(9L);
            StopFactor sameProduct = stopFactor(3, KIB_UNJUSTIFIED_DISPUTES, Map.of());
            sameProduct.setSegment(MSB);
            sameProduct.getProductIds().add(7L);

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L);
        }

        @Test
        void segmentIsReadFromTheProductRow() {
            assertThat(productRow("MSB").getSegment()).isEqualTo(MSB);
            assertThat(productRow("RETAIL").getSegment()).isEqualTo(RETAIL);
            assertThat(productRow(null).getSegment()).isEqualTo(ProductSegment.DEFAULT);
        }

        private ProductProjection productProjection(long id, ProductSegment segment) {
            ProductProjection projection = mock(ProductProjection.class);
            when(projection.getId()).thenReturn(id);
            when(projection.getSegment()).thenReturn(segment);
            return projection;
        }

        private static ProductProjection productRow(String segmentType) {
            return new ProductProjection() {
                public Long getId() { return 1L; }
                public String getName() { return "Продукт"; }
                public String getDescription() { return "Описание"; }
                public String getSegmentType() { return segmentType; }
            };
        }
    }

    @Nested
    @DisplayName("save, saveCondition, findByScoringId")
    class OtherMethods {

        @Test
        void saveCreatesAStopFactorOfTheMetricWithThisName() {
            StopFactorMetric metric = new StopFactorMetric();
            metric.setId(2L);
            when(metricRepository.findFirstByTitleOrNameTemplateOrderById("Количество активных споров КИБ", "Количество активных споров КИБ"))
                    .thenReturn(Optional.of(metric));

            service.save("Количество активных споров КИБ", "описание");

            verify(consoleService).create(new StopFactorRequest(2L, "Количество активных споров КИБ", "описание",
                    null, true, Set.of(), null, List.of(new RuleDto(Map.of(), List.of())), null), StopFactorServiceImpl.SPR_USER);
        }

        @Test
        void saveWithoutAMatchingMetricFails() {
            when(metricRepository.findFirstByTitleOrNameTemplateOrderById(any(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.save("Неизвестный", null)).isInstanceOf(BusinessValidationException.class);
            verifyNoInteractions(consoleService);
        }

        @Test
        void saveConditionCreatesANewVersionReplacingTheConditionWithTheSameResult() {
            StopFactorResponse current = new StopFactorResponse(9, 9, 1, true, "Текущее количество дней просрочки", null,
                    true, 9, 6, "Текущие дни просрочки", CURRENT_OVERDUE_DAYS, Set.of(5L), MSB,
                    List.of(new RuleDto(40L, "Текущее количество дней просрочки", Map.of(), List.of(
                            new ConditionDto(11L, REJECTED, INTEGER, "2", ConditionOperator.GT, null),
                            new ConditionDto(12L, UNDERWRITER, INTEGER, "1", ConditionOperator.RANGE, "2")))),
                    null, null, null, null, null);
            when(consoleService.get(9)).thenReturn(current);

            service.saveCondition(new StopFactorConditionDto(9, "5", INTEGER, REJECTED));

            ArgumentCaptor<StopFactorRequest> request = ArgumentCaptor.forClass(StopFactorRequest.class);
            verify(consoleService).update(eq(9L), request.capture(), eq(StopFactorServiceImpl.SPR_USER));
            // legacy knows only thresholds: the console sets UNDERWRITER RANGE 1..5 from the new REJECTED value
            assertThat(request.getValue().rules()).hasSize(1);
            assertThat(request.getValue().rules().getFirst().name()).isEqualTo("Текущее количество дней просрочки");
            assertThat(request.getValue().rules().getFirst().conditions()).containsExactly(
                    new ConditionDto(12L, UNDERWRITER, INTEGER, "1"),
                    new ConditionDto(null, REJECTED, INTEGER, "5"));
            assertThat(request.getValue().metricId()).isEqualTo(6L);
            assertThat(request.getValue().name()).isEqualTo("Текущее количество дней просрочки");
            assertThat(request.getValue().productIds()).containsExactly(5L);
            assertThat(request.getValue().segment()).isEqualTo(MSB);
            assertThat(request.getValue().changeReason()).isNotBlank();
        }

        @Test
        void findByScoringIdReturnsTheSavedChecks() {
            StopFactorProjection projection = mock(StopFactorProjection.class);
            when(checkRepository.findByScoringIdForProjection(SCORING_ID)).thenReturn(List.of(projection));

            assertThat(service.findByScoringId(SCORING_ID)).containsExactly(projection);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------------

    /** Version 1 (legacy id or a new stop factor), as changelog 1.3 leaves it. */
    private StopFactor stopFactor(long id, CalculatorType calculator, Map<String, Object> params) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setCalculator(calculator);
        metric.setParams(registry.get(calculator).paramSpecs().stream().map(ParamSpec::toSchema).toList());
        StopFactor stopFactor = new StopFactor("stop factor " + id, null);
        stopFactor.setId(id);
        stopFactor.setMetric(metric);
        StopFactorRule rule = stopFactor.addRule();
        rule.setName("stop factor " + id);
        rule.setParams(params);
        activeStopFactors.add(stopFactor);
        return stopFactor;
    }

    /** Version 2 of stop factor rootId, created by a console edit. */
    private StopFactor version(long id, long rootId, CalculatorType calculator, Map<String, Object> params) {
        StopFactor stopFactor = stopFactor(id, calculator, params);
        stopFactor.setRootId(rootId);
        stopFactor.setVersionNumber(2);
        return stopFactor;
    }

    /**
     * A condition as changelog 1.6 and the console store it: IN for loan statuses, GT for numbers, and an
     * UNDERWRITER number with a REJECTED number is RANGE up to the REJECTED value.
     */
    private void condition(StopFactor stopFactor, StopFactorStatus result, StopFactorValueTypes type, String value) {
        StopFactorRule rule = stopFactor.getRules().getFirst();
        rule.addCondition(result, type, value, type == ARRAY || type == STRING ? ConditionOperator.IN : ConditionOperator.GT, null);

        rule.getConditions().stream()
                .filter(c -> c.getResult() == REJECTED && c.getOperator() == ConditionOperator.GT)
                .findFirst()
                .ifPresent(rejected -> rule.getConditions().stream()
                        .filter(c -> c.getResult() == UNDERWRITER && c.getOperator() != ConditionOperator.IN)
                        .forEach(underwriter -> {
                            underwriter.setOperator(ConditionOperator.RANGE);
                            underwriter.setValueTo(rejected.getValue());
                        }));
    }

    /** Legacy stop factor 1 with its seed conditions. */
    private void kibStatusesStopFactor() {
        StopFactor kib = stopFactor(1, KIB_REFUSAL_STATUSES, Map.of());
        condition(kib, REJECTED, ARRAY, "WrittenOff, SoldToThirdParty, OpenUnderLegalProceedings");
        condition(kib, UNDERWRITER, ARRAY, "TransferredToOtherReportingInst");
    }

    /** KIB overdue counts: only "months 1-3, overdue 1-15 days" (read by legacy stop factor 27) is set. */
    private StopFactorDecision check(Application application) {
        return service.check(SCORING_ID,
                application.withKib ? application.customReport : null,
                application.withKib ? new IndividualReport(application.generalInfo, application.loans) : null,
                application.withKib ? application.consolidation : null,
                new TotalDebtFinalResponse(application.obCredits),
                REQUEST_DATE);
    }

    @SuppressWarnings("unchecked")
    private List<StopFactorCheck> savedChecks() {
        ArgumentCaptor<List<StopFactorCheck>> captor = ArgumentCaptor.forClass(List.class);
        verify(checkRepository, atLeastOnce()).saveAll(captor.capture());
        return captor.getValue();
    }

    /** The KIB and OB reports SPR passes to check(...), empty unless set. */
    private static final class Application {
        private boolean withKib = true;
        private GeneralInfoDto generalInfo = new GeneralInfoDto(0, 0, 0);
        private final List<LoanDto> loans = new ArrayList<>();
        private ConsolidationDto consolidation = new ConsolidationDto(BigDecimal.ZERO, 0, 0);
        private final List<TotalDebtReportData> obCredits = new ArrayList<>();
        private CustomReport customReport = new CustomReport();

        Application withoutKib() {
            withKib = false;
            return this;
        }

        Application activeDisputes(int count) {
            generalInfo = new GeneralInfoDto(count, generalInfo.numberOfUnjustifiedDisputesInPast(), generalInfo.creditInquiriesLast7Days());
            return this;
        }

        Application bureauRequests(int last7Days) {
            generalInfo = new GeneralInfoDto(generalInfo.numberOfActiveDisputes(), generalInfo.numberOfUnjustifiedDisputesInPast(), last7Days);
            return this;
        }

        Application loan(RoleOfClient role, LoanStatus status) {
            return loan(LoanDto.builder()
                    .participationType(role)
                    .loanStatus(status)
                    .phaseOfLoan(LoanPhase.Open)
                    .build());
        }

        Application loan(LoanDto loan) {
            loans.add(loan);
            return this;
        }

        Application consolidation(String overdueAmount, int newLoansLast30Days, int openCredits) {
            consolidation = new ConsolidationDto(new BigDecimal(overdueAmount), newLoansLast30Days, openCredits);
            return this;
        }

        /** Contracts of the KIB custom report, with their payment calendars. */
        Application contracts(Contract... contracts) {
            customReport = new CustomReport(new Contracts(new ContractList(List.of(contracts))));
            return this;
        }

        Application obCredit(long overdueDays, String overdueSum, LocalDate lastChangeDate) {
            obCredits.add(new TotalDebtReportData(overdueDays, overdueSum != null ? new BigDecimal(overdueSum) : null, lastChangeDate));
            return this;
        }
    }
}
