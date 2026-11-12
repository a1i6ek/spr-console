package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
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
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.config.CalculatorConfig;
import kg.bakai.stopfactor.calculator.KibRefusalStatusesCalculator;
import kg.bakai.stopfactor.calculator.OverdueBucketCountCalculator;
import kg.bakai.stopfactor.calculator.RestructuringCountCalculator;
import kg.bakai.stopfactor.condition.ConditionEvaluator;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCheck;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests of the stop factor check, no database. Stop factors are built the way changelogs 1.3 / 1.4 leave them:
 * legacy ids 1..34 are stop factors of group versions with a metric, params and thresholds. SPR returns no overdue overdue
 * months (null) unless a test sets them, so overdue stop factors read the legacy counters.
 */
class StopFactorServiceImplTest {

    private static final long SCORING_ID = 1L;
    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 11, 3);

    private final StopFactorGroupRepository stopFactorRepository = mock(StopFactorGroupRepository.class);
    private final StopFactorCheckRepository checkRepository = mock(StopFactorCheckRepository.class);
    private final KibService kibService = mock(KibService.class);
    private final KibOverdueHistoryReader overdueHistoryReader = mock(KibOverdueHistoryReader.class);

    private final List<StopFactorGroup> groups = new ArrayList<>();
    private OverdueForStopFactor kibOverdue = overdue(0);
    private List<CreditHistoryEvents.OverdueMonth> kibOverdueMonths = null;
    private Integer kibCompleteMonths = null;

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
        service = new StopFactorServiceImpl(stopFactorRepository, checkRepository,
                new CheckContextFactory(List.of(new ReportValuesProvider(), new LegacyCountersProvider(kibService),
                new CreditHistoryProvider(overdueHistoryReader), new LoanStatusesProvider())), registry);

        when(stopFactorRepository.findAllCurrentActive()).thenReturn(groups);
        when(stopFactorRepository.findProductByScoringId(anyLong())).thenReturn(Optional.empty());
        when(kibService.getOverdueByPeriodAndCount(any(), any())).thenAnswer(invocation -> kibOverdue);
        when(overdueHistoryReader.read(any(), any()))
                .thenAnswer(invocation -> kibOverdueMonths != null ? new OverdueHistory(kibOverdueMonths, kibCompleteMonths) : null);
    }

    @Nested
    @DisplayName("Legacy rules (same as StopFactorLegacyServiceImpl)")
    class LegacyRules {

        @Test
        void approvedWithNullCodeWhenNothingFires() {
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(APPROVED, null));
        }

        @Test
        void numberConditionFiresOnlyWhenValueIsGreater() {
            StopFactor bureau = group(8, BUREAU_REQUESTS_COUNT, Map.of("days", 7));
            condition(bureau, REJECTED, INTEGER, "14");

            assertThat(check(new Application().bureauRequests(14)).status()).isEqualTo(APPROVED);
            assertThat(check(new Application().bureauRequests(15)).status()).isEqualTo(REJECTED);
        }

        @Test
        void underwriterConditionIsCheckedOnlyWhenNotRejected() {
            // legacy stop factor 27: REJECTED > 9, UNDERWRITER > 6
            StopFactor bucket = group(27, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 15));
            condition(bucket, REJECTED, INTEGER, "9");
            condition(bucket, UNDERWRITER, INTEGER, "6");

            kibOverdue = overdue(6);
            assertThat(check(new Application()).status()).isEqualTo(APPROVED);
            kibOverdue = overdue(7);
            assertThat(check(new Application()).status()).isEqualTo(UNDERWRITER);
            kibOverdue = overdue(10);
            assertThat(check(new Application()).status()).isEqualTo(REJECTED);
        }

        @Test
        void rejectedWinsOverUnderwriterOfAnotherStopFactor() {
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            StopFactor openCredits = group(12, OPEN_CREDITS_COUNT, Map.of());
            condition(openCredits, UNDERWRITER, INTEGER, "0");

            StopFactorDecision decision = check(new Application().activeDisputes(1).consolidation("0", 0, 1));

            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getResult).containsExactly(REJECTED, UNDERWRITER);
        }

        @Test
        void anyUnderwriterSendsToUnderwriterWithNullCode() {
            StopFactor openCredits = group(12, OPEN_CREDITS_COUNT, Map.of());
            condition(openCredits, UNDERWRITER, INTEGER, "8");

            assertThat(check(new Application().consolidation("0", 0, 9)))
                    .isEqualTo(new StopFactorDecision(UNDERWRITER, null));
        }

        @Test
        void currentOverdueDaysGive451EvenTogetherWithOtherRejectedStopFactors() {
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            StopFactor overdueDays = group(9, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDays, REJECTED, INTEGER, "0");

            assertThat(check(new Application().activeDisputes(1).obCredit(3, null, null)))
                    .isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
        }

        @Test
        void currentOverdueAmountGives451() {
            StopFactor overdueAmount = group(10, CURRENT_OVERDUE_AMOUNT, Map.of());
            condition(overdueAmount, REJECTED, DOUBLE, "0");

            assertThat(check(new Application().obCredit(0, "0.01", null)))
                    .isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
        }

        @Test
        void otherRejectedStopFactorsKeepNullCode() {
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
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
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            group(9, CURRENT_OVERDUE_DAYS, Map.of());

            StopFactorDecision decision = check(new Application().withoutKib().obCredit(4, null, null));

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue)
                    .containsExactly(ConditionEvaluator.NO_MATCHES, "0.0", "4.0");
            verifyNoInteractions(kibService);
        }

        @Test
        void valuesAreTakenFromTheReportsLikeLegacy() {
            group(4, RESTRUCTURING_COUNT, Map.of("source", "KIB", "periodFromMonth", 1, "periodToMonth", 12));
            group(5, RESTRUCTURING_COUNT, Map.of("source", "OB", "periodFromMonth", 1, "periodToMonth", 12));
            group(7, RESTRUCTURING_COUNT, Map.of("source", "OB", "periodFromMonth", 13, "periodToMonth", 24));
            group(9, CURRENT_OVERDUE_DAYS, Map.of());
            group(10, CURRENT_OVERDUE_AMOUNT, Map.of());

            check(new Application()
                    .loan(new LoanDto(MainDebtor, Open, 2, 0, 5))
                    .loan(new LoanDto(Guarantor, Open, 1, 0, 12))
                    .loan(new LoanDto(CoDebtor, Open, 10, 0, 99))                 // not the client's own loan
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
            verify(kibService).getOverdueByPeriodAndCount(any(CustomReport.class), eq(REQUEST_DATE.atStartOfDay()));
        }

        @Test
        void stopFactorWithoutConditionsApproves() {
            group(12, OPEN_CREDITS_COUNT, Map.of());

            assertThat(check(new Application().consolidation("0", 0, 100)).status()).isEqualTo(APPROVED);
        }

        @Test
        void everyActiveStopFactorIsSavedInOrderForTheScoring() {
            group(1, KIB_REFUSAL_STATUSES, Map.of());
            group(2, KIB_ACTIVE_DISPUTES, Map.of());
            group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of());

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(1L, 2L, 3L);
            assertThat(savedChecks()).allMatch(c -> c.getScoringId() == SCORING_ID);
        }
    }

    @Nested
    @DisplayName("Console edits, new stop factors, NO_DATA")
    class NewStructure {

        @Test
        void aChangeIsANewGroupVersionAndOldChecksKeepPointingToTheRuleTheyRanWith() {
            StopFactor overdueDays = group(9, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDays, REJECTED, INTEGER, "0");
            check(new Application().obCredit(3, null, null));
            StopFactorCheck before = savedChecks().getFirst();

            // the console closed version 1 and created version 2 with a copy of the rule, rejecting only above 5 days
            groups.clear();
            StopFactor version2 = group(35, CURRENT_OVERDUE_DAYS, Map.of());
            condition(version2, REJECTED, INTEGER, "5");
            StopFactorDecision decision = check(new Application().obCredit(3, null, null));

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(before.getResult()).isEqualTo(REJECTED);
            assertThat(before.getStopFactor()).isSameAs(overdueDays);
            assertThat(before.getStopFactor().getConditions()).singleElement().extracting(c -> c.getValue()).isEqualTo("0");
            assertThat(savedChecks().getFirst().getStopFactor().getId()).isEqualTo(35L);
        }

        @Test
        void aCurrentOverdueStopFactorAddedInTheConsoleGives451() {
            StopFactor overdueDays = group(35, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDays, REJECTED, INTEGER, "5");

            assertThat(check(new Application().obCredit(6, null, null))).isEqualTo(new StopFactorDecision(REJECTED, InternalStatusCode._451));
            assertThat(savedChecks().getFirst().getStopFactor().getId()).isEqualTo(35L);
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("6.0");
        }

        @Test
        void newStopFactorOfALegacyMetricGetsTheLegacyValue() {
            // created in the console, any id, no case in the legacy switch
            StopFactor newDisputes = group(100, KIB_ACTIVE_DISPUTES, Map.of());
            condition(newDisputes, UNDERWRITER, INTEGER, "1");

            assertThat(check(new Application().activeDisputes(2))).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("2.0");
        }

        @Test
        void periodWithoutALegacyValueGoesToUnderwriterAsNoData() {
            // months 1..2 have no legacy counter (LegacyOverdueCounters)
            StopFactor bucket = group(101, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 2, "minOverdueDays", 1));
            condition(bucket, REJECTED, INTEGER, "0");

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo(StopFactorServiceImpl.NO_DATA);
        }

        @Test
        void noDataDoesNotOverrideRejected() {
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            group(101, OVERDUE_BUCKET_COUNT, Map.of("periodFromMonth", 1, "periodToMonth", 2, "minOverdueDays", 1));

            assertThat(check(new Application().activeDisputes(1))).isEqualTo(new StopFactorDecision(REJECTED, null));
        }

        @Test
        void statusSavedInTheConsoleWithoutSpacesWorks() {
            StopFactor kib = group(1, KIB_REFUSAL_STATUSES, Map.of());
            condition(kib, REJECTED, ARRAY, "WrittenOff,SoldToThirdParty");

            assertThat(check(new Application().loan(MainDebtor, SoldToThirdParty)).status()).isEqualTo(REJECTED);
        }

        @Test
        void unknownStatusInAConditionIsSkippedInsteadOfFailingTheScoring() {
            StopFactor kib = group(1, KIB_REFUSAL_STATUSES, Map.of());
            condition(kib, REJECTED, ARRAY, "NoSuchStatus, WrittenOff");

            assertThat(check(new Application().loan(MainDebtor, WrittenOff)).status()).isEqualTo(REJECTED);
        }

        @Test
        void stringConditionIsAOneStatusList() {
            StopFactor kib = group(1, KIB_REFUSAL_STATUSES, Map.of());
            condition(kib, REJECTED, STRING, "WrittenOff");

            assertThat(check(new Application().loan(Guarantor, WrittenOff)).status()).isEqualTo(REJECTED);
        }

        @Test
        void repeatedCheckReplacesPreviousResultsOfTheScoring() {
            group(2, KIB_ACTIVE_DISPUTES, Map.of());

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
            StopFactor bucket = group(200, OVERDUE_BUCKET_COUNT,
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
        void overdueMonthsReplaceTheLegacyCounters() {
            // legacy stop factor 27 (months 1-3, overdue 1-15) reads the KIB counter; with overdue months it counts them
            StopFactor bucket = group(27, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 15));
            condition(bucket, REJECTED, INTEGER, "0");
            kibOverdue = overdue(10);
            kibOverdueMonths = List.of();

            assertThat(check(new Application()).status()).isEqualTo(APPROVED);
            verify(overdueHistoryReader).read(any(CustomReport.class), eq(REQUEST_DATE.atStartOfDay()));
        }

        @Test
        void withoutKibReportEveryOverdueCountIsZero() {
            // no credit history: 0 like the legacy counters, also for a range without a legacy counter
            StopFactor bucket = group(201, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 11, "maxOverdueDays", 20));
            condition(bucket, REJECTED, INTEGER, "0");

            assertThat(check(new Application().withoutKib())).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo("0.0");
        }

        @Test
        void sprWithoutOverdueMonthsSendsANewRangeToTheUnderwriter() {
            StopFactor bucket = group(201, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 11, "maxOverdueDays", 20));
            condition(bucket, REJECTED, INTEGER, "0");
            kibOverdueMonths = null;

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks().getFirst().getCheckedValue()).isEqualTo(StopFactorServiceImpl.NO_DATA);
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

    @Nested
    @DisplayName("Products (public.scoring -> public.product)")
    class Products {

        @Test
        void scoringWithoutProductChecksAllStopFactorsLikeLegacy() {
            onlyFactor(group(2, KIB_ACTIVE_DISPUTES, Map.of())).getProductIds().add(5L);
            group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of());

            check(new Application());

            assertThat(savedChecks()).hasSize(2).allMatch(c -> c.getProductId() == null);
        }

        @Test
        void stopFactorLimitedToAnotherProductIsSkipped() {
            ProductProjection product = productProjection(7L, RETAIL);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            onlyFactor(group(2, KIB_ACTIVE_DISPUTES, Map.of())).getProductIds().add(5L);
            onlyFactor(group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of())).getProductIds().add(7L);
            group(4, KIB_ACTIVE_DISPUTES, Map.of());   // no products = every product

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L, 4L);
            assertThat(savedChecks()).allMatch(c -> c.getProductId() == 7L);
        }

        @Test
        void stopFactorOfAnotherSegmentIsSkipped() {
            ProductProjection product = productProjection(7L, MSB);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            onlyFactor(group(2, KIB_ACTIVE_DISPUTES, Map.of())).setSegment(RETAIL);
            onlyFactor(group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of())).setSegment(MSB);
            group(4, KIB_ACTIVE_DISPUTES, Map.of());   // no segment = every segment

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L, 4L);
        }

        @Test
        void onlyTheStopFactorsThatApplyAreChecked() {
            ProductProjection product = productProjection(7L, MSB);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            group(2, KIB_ACTIVE_DISPUTES, Map.of()).setSegment(RETAIL);
            group(3, KIB_ACTIVE_DISPUTES, Map.of()).setSegment(MSB);
            group(4, KIB_ACTIVE_DISPUTES, Map.of()).getProductIds().add(9L);

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L);
        }

        @Test
        void scoringWithoutProductGetsTheDefaultSegment() {
            onlyFactor(group(2, KIB_ACTIVE_DISPUTES, Map.of())).setSegment(RETAIL);
            onlyFactor(group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of())).setSegment(MSB);

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(2L);
        }

        @Test
        void segmentAndProductLimitsMustBothMatch() {
            ProductProjection product = productProjection(7L, MSB);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            StopFactor otherProduct = onlyFactor(group(2, KIB_ACTIVE_DISPUTES, Map.of()));
            otherProduct.setSegment(MSB);
            otherProduct.getProductIds().add(9L);
            StopFactor sameProduct = onlyFactor(group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of()));
            sameProduct.setSegment(MSB);
            sameProduct.getProductIds().add(7L);

            check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L);
        }

        @Test
        void segmentIsReadFromTheProductRow() {
            assertThat(productRow("MSB").getSegment()).isEqualTo(MSB);
            assertThat(productRow("BUSINESS").getSegment()).isEqualTo(MSB);
            assertThat(productRow("RETAIL").getSegment()).isEqualTo(RETAIL);
            assertThat(productRow("IFC").getSegment()).isEqualTo(RETAIL);
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
    @DisplayName("Check flow: stop factors, decision")
    class CheckFlow {

        @Test
        void withoutActiveStopFactorsTheApplicationIsApprovedAndOldResultsAreRemoved() {
            StopFactorDecision decision = check(new Application().activeDisputes(5));

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            verify(checkRepository).deleteByScoringId(SCORING_ID);
            assertThat(savedChecks()).isEmpty();
        }

        @Test
        void everyStopFactorIsCheckedAndSavedInItsOrder() {
            StopFactor overdue = group(13, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10));
            StopFactor longer = group(14, OVERDUE_BUCKET_COUNT, Map.of("periodFromMonth", 4, "periodToMonth", 6, "minOverdueDays", 1));
            condition(longer, REJECTED, INTEGER, "1");
            kibOverdueMonths = List.of(overdueMonth(2, 5), overdueMonth(5, 3), overdueMonth(6, 3));

            StopFactorDecision decision = check(new Application());

            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(13L, 14L);
            assertThat(savedChecks()).extracting(StopFactorCheck::getCheckedValue).containsExactly("1.0", "2.0");
            assertThat(savedChecks()).extracting(StopFactorCheck::getResult).containsExactly(APPROVED, REJECTED);
            assertThat(decision).isEqualTo(new StopFactorDecision(REJECTED, null));
        }

        @Test
        void anInactiveStopFactorIsNeitherCheckedNorSaved() {
            StopFactor disputes = group(2, KIB_ACTIVE_DISPUTES, Map.of());
            condition(disputes, REJECTED, INTEGER, "0");
            onlyFactor(disputes).setActive(false);
            group(3, KIB_UNJUSTIFIED_DISPUTES, Map.of());

            StopFactorDecision decision = check(new Application().activeDisputes(5));

            assertThat(decision).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).extracting(c -> c.getStopFactor().getId()).containsExactly(3L);
        }

        @Test
        void currentOverdueSendingToTheUnderwriterGivesNo451() {
            StopFactor overdueDays = group(9, CURRENT_OVERDUE_DAYS, Map.of());
            condition(overdueDays, UNDERWRITER, INTEGER, "0");

            assertThat(check(new Application().obCredit(3, null, null)))
                    .isEqualTo(new StopFactorDecision(UNDERWRITER, null));
        }

        @Test
        void eachCheckKeepsTheScoringTheProductAndTheMeasuredValue() {
            ProductProjection product = mock(ProductProjection.class);
            when(product.getId()).thenReturn(7L);
            when(product.getSegment()).thenReturn(RETAIL);
            when(stopFactorRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));
            group(12, OPEN_CREDITS_COUNT, Map.of());

            check(new Application().consolidation("0", 0, 4));

            assertThat(savedChecks()).singleElement().satisfies(c -> {
                assertThat(c.getScoringId()).isEqualTo(SCORING_ID);
                assertThat(c.getProductId()).isEqualTo(7L);
                assertThat(c.getCheckedValue()).isEqualTo("4.0");
                assertThat(c.getResult()).isEqualTo(APPROVED);
            });
        }

        @Test
        void noDataOfAStopFactorWithoutConditionsDecidesNothing() {
            // a range the requirements leave without limit: measured and saved, it never decides
            group(201, OVERDUE_BUCKET_COUNT, Map.of("periodFromMonth", 7, "periodToMonth", 12, "minOverdueDays", 1, "maxOverdueDays", 5));
            kibOverdueMonths = null;

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(APPROVED, null));
            assertThat(savedChecks()).singleElement().satisfies(c -> {
                assertThat(c.getCheckedValue()).isEqualTo(StopFactorServiceImpl.NO_DATA);
                assertThat(c.getResult()).isEqualTo(APPROVED);
            });
        }

        @Test
        void noDataOfAStopFactorWithConditionsGoesToTheUnderwriter() {
            StopFactor bucket = group(201, OVERDUE_BUCKET_COUNT,
                    Map.of("periodFromMonth", 7, "periodToMonth", 12, "minOverdueDays", 6, "maxOverdueDays", 10));
            condition(bucket, REJECTED, INTEGER, "8");
            kibOverdueMonths = null;

            assertThat(check(new Application())).isEqualTo(new StopFactorDecision(UNDERWRITER, null));
            assertThat(savedChecks()).singleElement().extracting(StopFactorCheck::getResult).isEqualTo(UNDERWRITER);
        }

        private static CreditHistoryEvents.OverdueMonth overdueMonth(int monthsAgo, int maxOverdueDays) {
            return new CreditHistoryEvents.OverdueMonth(monthsAgo, maxOverdueDays);
        }
    }

    @Nested
    @DisplayName("findByScoringId")
    class OtherMethods {

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

    /** The only stop factor of a current active group (legacy id or a new one): no thresholds until condition(...). */
    private StopFactor group(long id, CalculatorType calculator, Map<String, Object> params) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setCalculator(calculator);
        StopFactorGroup group = new StopFactorGroup();
        group.setId(id);
        StopFactor factor = group.addFactor();
        factor.setId(id);
        factor.setMetric(metric);
        factor.setName("stop factor " + id);
        factor.setParams(new LinkedHashMap<>(params));
        groups.add(group);
        return factor;
    }

    private static StopFactor onlyFactor(StopFactor factor) {
        return factor;
    }

    /**
     * A condition as changelog 1.3 and the console store it: IN for loan statuses, GT for numbers, and an UNDERWRITER
     * number below a REJECTED number is RANGE up to the REJECTED value.
     */
    private static void condition(StopFactor factor, StopFactorStatus result, StopFactorValueTypes type, String value) {
        boolean list = type == ARRAY || type == STRING;
        Optional<String> rejectedAbove = factor.getConditions().stream()
                .filter(c -> c.getResult() == REJECTED && c.getOperator() == ConditionOperator.GT)
                .map(c -> c.getValue())
                .findFirst();
        if (result == UNDERWRITER && !list && rejectedAbove.isPresent()) {
            factor.addCondition(UNDERWRITER, ConditionOperator.RANGE, value, rejectedAbove.get(), type);
        } else {
            factor.addCondition(result, list ? ConditionOperator.IN : ConditionOperator.GT, value, null, type);
        }
    }

    /** Legacy stop factor 1 with its seed conditions. */
    private void kibStatusesStopFactor() {
        StopFactor kib = group(1, KIB_REFUSAL_STATUSES, Map.of());
        condition(kib, REJECTED, ARRAY, "WrittenOff, SoldToThirdParty, OpenUnderLegalProceedings");
        condition(kib, UNDERWRITER, ARRAY, "TransferredToOtherReportingInst");
    }

    /** KIB overdue counts: only "months 1-3, overdue 1-15 days" (read by legacy stop factor 27) is set. */
    private static OverdueForStopFactor overdue(int months1To3Days1To15) {
        return new OverdueForStopFactor(months1To3Days1To15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private StopFactorDecision check(Application application) {
        return service.check(SCORING_ID,
                application.withKib ? new CustomReport() : null,
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
            return loan(new LoanDto(role, status, 0, 0, 0));
        }

        Application loan(LoanDto loan) {
            loans.add(loan);
            return this;
        }

        Application consolidation(String overdueAmount, int newLoansLast30Days, int openCredits) {
            consolidation = new ConsolidationDto(new BigDecimal(overdueAmount), newLoansLast30Days, openCredits);
            return this;
        }

        Application obCredit(long overdueDays, String overdueSum, LocalDate lastChangeDate) {
            obCredits.add(new TotalDebtReportData(overdueDays, overdueSum != null ? new BigDecimal(overdueSum) : null, lastChangeDate));
            return this;
        }
    }
}
