package kg.bakai.console.test;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.console.test.TestCheckRequest.CalendarEntry;
import kg.bakai.console.test.TestCheckRequest.Contract;
import kg.bakai.console.test.TestCheckRequest.Kib;
import kg.bakai.console.test.TestCheckRequest.KibPart;
import kg.bakai.console.test.TestCheckRequest.Loan;
import kg.bakai.console.test.TestCheckRequest.ObCredit;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.kib.dto.OverdueForStopFactor;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.CalendarItem;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.report.CreditHistoryEvents.OverdueMonth;
import kg.bakai.stopfactor.service.StopFactorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static kg.bakai.stopfactor.enums.StopFactorStatus.APPROVED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The test check without a database: which reports it builds for StopFactorService.check and what it answers. */
class StopFactorTestCheckServiceTest {

    private static final long SCORING_ID = 1;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 4);

    private final StopFactorService stopFactorService = mock(StopFactorService.class);
    private final StopFactorGroupRepository groupRepository = mock(StopFactorGroupRepository.class);
    private final StopFactorCheckRepository checkRepository = mock(StopFactorCheckRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final StopFactorTestCheckService service =
            new StopFactorTestCheckService(stopFactorService, groupRepository, checkRepository, jdbcTemplate);

    @BeforeEach
    void setUp() {
        decides(APPROVED, null);
        when(groupRepository.findProductByScoringId(anyLong())).thenReturn(Optional.empty());
        when(stopFactorService.findByScoringId(anyLong())).thenReturn(List.of());
    }

    @Nested
    class Scoring {

        @Test
        void theGivenScoringIsChecked() {
            TestCheckResponse response = service.check(request(SCORING_ID, null, kib()));

            assertThat(response.scoringId()).isEqualTo(SCORING_ID);
            verify(stopFactorService).check(eq(SCORING_ID), any(), any(), any(), any(), any());
        }

        @Test
        void withoutScoringANewNegativeTestScoringIsUsedEachTime() {
            long first = service.check(request(null, null, kib())).scoringId();
            long second = service.check(request(null, null, kib())).scoringId();

            assertThat(first).isNegative();
            assertThat(second).isEqualTo(first - 1);
        }

        @Test
        void theRequestDateIsTodayWhenLeftOut() {
            service.check(new TestCheckRequest(SCORING_ID, null, null, kib(), List.of()));

            assertThat(captured().requestDate()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    class Product {

        @Test
        void aProductOfTheRequestIsLinkedToTheScoring() {
            ProductProjection product = product(3, "Онлайн кредит для ИП", ProductSegment.MSB);
            when(groupRepository.findProductsByIds(List.of(3L))).thenReturn(List.of(product));

            service.check(request(-5L, 3L, kib()));

            verify(jdbcTemplate).update(contains("INSERT INTO public.scoring"), eq(-5L), eq(3L));
        }

        @Test
        void anUnknownProductFailsBeforeTheCheck() {
            when(groupRepository.findProductsByIds(List.of(9L))).thenReturn(List.of());

            assertThatThrownBy(() -> service.check(request(-5L, 9L, kib())))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Продукт 9 не найден");
            verifyNoInteractions(jdbcTemplate);
            verify(stopFactorService, never()).check(anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void theProductOfTheScoringGivesItsSegment() {
            ProductProjection product = product(5, "Бизнес лимит", ProductSegment.MSB);
            when(groupRepository.findProductByScoringId(SCORING_ID)).thenReturn(Optional.of(product));

            TestCheckResponse response = service.check(request(SCORING_ID, null, kib()));

            assertThat(response.productId()).isEqualTo(5L);
            assertThat(response.productName()).isEqualTo("Бизнес лимит");
            assertThat(response.segment()).isEqualTo(ProductSegment.MSB);
            assertThat(response.imitated()).noneMatch(line -> line.contains("без продукта"));
        }

        @Test
        void aScoringWithoutProductIsTheDefaultSegment() {
            TestCheckResponse response = service.check(request(SCORING_ID, null, kib()));

            assertThat(response.productId()).isNull();
            assertThat(response.productName()).isNull();
            assertThat(response.segment()).isEqualTo(ProductSegment.DEFAULT);
            assertThat(response.imitated()).anyMatch(line -> line.startsWith("Скоринг 1 без продукта"));
        }
    }

    @Nested
    class KibReport {

        @Test
        void withoutKibEveryKibPartIsMissing() {
            TestCheckResponse response = service.check(request(SCORING_ID, null, null));

            Reports reports = captured();
            assertThat(reports.customReport()).isNull();
            assertThat(reports.individualReport()).isNull();
            assertThat(reports.consolidation()).isNull();
            assertThat(response.imitated()).anyMatch(line -> line.startsWith("Нет отчёта КИБ"));
        }

        @Test
        void theValuesOfTheRequestReachTheReports() {
            OverdueForStopFactor legacy = new OverdueForStopFactor(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
            Kib kib = new Kib(2, 1, 4, new BigDecimal("150.75"), 3, 6, List.of(), null, null, false, legacy, null);

            service.check(request(SCORING_ID, null, kib));

            Reports reports = captured();
            assertThat(reports.individualReport().generalInfoDto().numberOfActiveDisputes()).isEqualTo(2);
            assertThat(reports.individualReport().generalInfoDto().numberOfUnjustifiedDisputesInPast()).isEqualTo(1);
            assertThat(reports.individualReport().generalInfoDto().creditInquiriesLast7Days()).isEqualTo(4);
            assertThat(reports.consolidation()).isEqualTo(new ConsolidationDto(new BigDecimal("150.75"), 3, 6));
            assertThat(reports.customReport().overdue()).isSameAs(legacy);
        }

        @Test
        void aMissingOverdueAmountIsZero() {
            service.check(request(SCORING_ID, null, kib()));

            assertThat(captured().consolidation().totalOverduePrincipalOrInterestOnAllOpenCreditContracts()).isEqualByComparingTo("0");
        }

        @Test
        void aLoanWithoutRoleAndStatusIsAnOpenLoanOfTheMainDebtor() {
            Kib kib = new Kib(0, 0, 0, null, 0, 0, List.of(new Loan(null, null, 1, 2, 3),
                    new Loan(RoleOfClient.Guarantor, LoanStatus.WrittenOff, 0, 0, 0)), null, null, false, null, null);

            service.check(request(SCORING_ID, null, kib));

            assertThat(captured().individualReport().loanDtoList())
                    .extracting(LoanDto::getParticipationType, LoanDto::getLoanStatus, LoanDto::getNumberOfRestructuringsLast12Months,
                            LoanDto::getNumberOfRestructuringsFrom12To24Months, LoanDto::getDaysDelay)
                    .containsExactly(tuple(RoleOfClient.MainDebtor, LoanStatus.Open, 1, 2, 3),
                            tuple(RoleOfClient.Guarantor, LoanStatus.WrittenOff, 0, 0, 0));
        }

        @Test
        void eachMissingPartIsLeftOutAndReported() {
            Kib kib = new Kib(1, 0, 0, null, 0, 0, null, null, null, false, null, Set.of(KibPart.CONSOLIDATION, KibPart.CUSTOM_REPORT));

            TestCheckResponse response = service.check(request(SCORING_ID, null, kib));

            Reports reports = captured();
            assertThat(reports.customReport()).isNull();
            assertThat(reports.individualReport()).isNotNull();
            assertThat(reports.consolidation()).isNull();
            assertThat(response.imitated()).anyMatch(line -> line.startsWith("Неполный отчёт КИБ, нет [CUSTOM_REPORT, CONSOLIDATION]"));
        }

        @Test
        void aMissingIndividualReportIsLeftOut() {
            Kib kib = new Kib(1, 0, 0, null, 0, 0, null, null, null, false, null, Set.of(KibPart.INDIVIDUAL_REPORT));

            service.check(request(SCORING_ID, null, kib));

            assertThat(captured().individualReport()).isNull();
            assertThat(captured().customReport()).isNotNull();
        }
    }

    @Nested
    class OverdueHistory {

        @Test
        void eachOverdueMonthIsAMainDebtorContractWithOneEntryInThatMonth() {
            Kib kib = new Kib(0, 0, 0, null, 0, 0, null, List.of(new OverdueMonth(1, 5), new OverdueMonth(3, 12)), null, false, null, null);

            service.check(request(SCORING_ID, null, kib));

            List<kg.bakai.spr.core.kib.dto.response.Contracts.Contract> contracts = contracts(captured().customReport());
            assertThat(contracts).extracting(c -> c.roleOfClient(), c -> c.startDate())
                    .containsExactly(tuple(RoleOfClient.MainDebtor, DATE.atStartOfDay()),
                            tuple(RoleOfClient.MainDebtor, DATE.minusMonths(2).atStartOfDay()));
            assertThat(contracts.get(1).paymentCalendarList().paymentCalendarItems())
                    .containsExactly(new CalendarItem(DATE.minusMonths(2).atStartOfDay(), 0, 12));
        }

        @Test
        void aRawContractKeepsItsCalendarRoleAndStart() {
            Contract guarantor = new Contract(RoleOfClient.Guarantor, LocalDate.of(2020, 1, 10),
                    List.of(new CalendarEntry(LocalDate.of(2026, 10, 31), 90, 0)));
            Contract withoutRoleAndStart = new Contract(null, null, List.of(
                    new CalendarEntry(LocalDate.of(2026, 10, 31), 12, 0),
                    new CalendarEntry(LocalDate.of(2026, 9, 30), 4, 4)));
            Kib kib = new Kib(0, 0, 0, null, 0, 0, null, null, List.of(guarantor, withoutRoleAndStart), false, null, null);

            service.check(request(SCORING_ID, null, kib));

            List<kg.bakai.spr.core.kib.dto.response.Contracts.Contract> contracts = contracts(captured().customReport());
            assertThat(contracts.get(0).roleOfClient()).isEqualTo(RoleOfClient.Guarantor);
            assertThat(contracts.get(0).startDate()).isEqualTo(LocalDateTime.of(2020, 1, 10, 0, 0));
            assertThat(contracts.get(0).paymentCalendarList().paymentCalendarItems())
                    .containsExactly(new CalendarItem(LocalDateTime.of(2026, 10, 31, 0, 0), 0, 90));
            // no role: the main debtor; no start: the first month of the calendar
            assertThat(contracts.get(1).roleOfClient()).isEqualTo(RoleOfClient.MainDebtor);
            assertThat(contracts.get(1).startDate()).isEqualTo(LocalDateTime.of(2026, 9, 30, 0, 0));
        }

        @Test
        void unavailableCalendarsKeepTheContractsWithoutTheirEntries() {
            Kib kib = new Kib(0, 0, 0, null, 0, 0, null, List.of(new OverdueMonth(2, 5)), null, true, null, null);

            TestCheckResponse response = service.check(request(SCORING_ID, null, kib));

            List<kg.bakai.spr.core.kib.dto.response.Contracts.Contract> contracts = contracts(captured().customReport());
            assertThat(contracts).singleElement().satisfies(contract -> {
                assertThat(contract.roleOfClient()).isEqualTo(RoleOfClient.MainDebtor);
                assertThat(contract.startDate()).isEqualTo(DATE.minusMonths(1).atStartOfDay());
                assertThat(contract.paymentCalendarList().paymentCalendarItems()).isEmpty();
            });
            assertThat(response.imitated()).anyMatch(line -> line.startsWith("В отчёте КИБ нет платежных календарей"));
        }

        @Test
        void unavailableCalendarsWithoutContractsGiveOneEmptyContract() {
            Kib kib = new Kib(0, 0, 0, null, 0, 0, null, null, null, true, null, null);

            service.check(request(SCORING_ID, null, kib));

            assertThat(contracts(captured().customReport())).singleElement().satisfies(contract -> {
                assertThat(contract.roleOfClient()).isEqualTo(RoleOfClient.MainDebtor);
                assertThat(contract.startDate()).isNull();
                assertThat(contract.paymentCalendarList().paymentCalendarItems()).isEmpty();
            });
        }

        @Test
        void withoutHistoryTheReportHasNoContracts() {
            service.check(request(SCORING_ID, null, kib()));

            assertThat(contracts(captured().customReport())).isEmpty();
        }
    }

    @Nested
    class ObReport {

        @Test
        void obCreditsReachTheReport() {
            ObCredit credit = new ObCredit(5, new BigDecimal("1200.50"), DATE.minusMonths(1));

            TestCheckResponse response = service.check(new TestCheckRequest(SCORING_ID, null, DATE, kib(), List.of(credit)));

            assertThat(captured().totalDebt().getCredits()).singleElement().satisfies(data -> {
                assertThat(data.getCurrentOverdueDaysCount()).isEqualTo(5);
                assertThat(data.getCurrentOverdueSum()).isEqualByComparingTo("1200.50");
                assertThat(data.getLastChangeDate()).isEqualTo(DATE.minusMonths(1));
            });
            assertThat(response.imitated()).noneMatch(line -> line.equals("Нет кредитов в отчёте ОБ"));
        }

        @Test
        void withoutObCreditsTheReportIsEmptyAndReported() {
            TestCheckResponse response = service.check(new TestCheckRequest(SCORING_ID, null, DATE, kib(), null));

            assertThat(captured().totalDebt().getCredits()).isEmpty();
            assertThat(response.imitated()).contains("Нет кредитов в отчёте ОБ");
        }
    }

    @Nested
    class Response {

        @Test
        void theDecisionAndItsCodeAreReturned() {
            decides(REJECTED, InternalStatusCode._451);

            TestCheckResponse response = service.check(request(SCORING_ID, null, kib()));

            assertThat(response.decision()).isEqualTo(REJECTED);
            assertThat(response.internalStatusCode()).isEqualTo(InternalStatusCode._451);
        }

        @Test
        void firedListsTheResultsThatDidNotApproveAndChecksListThemAll() {
            StopFactorProjection approved = projection(APPROVED, "Открытые кредиты", "2.0");
            StopFactorProjection underwriter = projection(UNDERWRITER, "Непрерывная просроченная задолженность", "2.0");
            StopFactorProjection rejected = projection(REJECTED, "Активные споры КИБ", "1.0");
            when(stopFactorService.findByScoringId(SCORING_ID)).thenReturn(List.of(approved, underwriter, rejected));

            TestCheckResponse response = service.check(request(SCORING_ID, null, kib()));

            assertThat(response.fired()).containsExactly(
                    "UNDERWRITER: Непрерывная просроченная задолженность (2.0)",
                    "REJECTED: Активные споры КИБ (1.0)");
            assertThat(response.checks()).containsExactly(approved, underwriter, rejected);
        }

        @Test
        void theResultsAreReadAfterTheyAreSaved() {
            service.check(request(SCORING_ID, null, kib()));

            InOrder order = inOrder(stopFactorService, checkRepository);
            order.verify(stopFactorService).check(eq(SCORING_ID), any(), any(), any(), any(), any());
            order.verify(checkRepository).flush();
            order.verify(stopFactorService).findByScoringId(SCORING_ID);
        }
    }

    @Nested
    class Scenarios {

        @Test
        void everyReadyScenarioIsListed() {
            assertThat(service.scenarios()).isSameAs(TestCheckScenarios.ALL).isNotEmpty();
            assertThat(service.scenarios()).extracting(TestCheckScenario::name).doesNotHaveDuplicates();
        }

        @Test
        void aScenarioWithItsExpectedDecisionIsAsExpected() {
            decides(REJECTED, InternalStatusCode._451);

            TestCheckScenarioResult result = service.runScenario("current-overdue-451");

            assertThat(result.scenario()).isEqualTo("current-overdue-451");
            assertThat(result.expectedDecision()).isEqualTo(REJECTED);
            assertThat(result.asExpected()).isTrue();
        }

        @Test
        void anotherDecisionOrCodeIsNotAsExpected() {
            decides(REJECTED, null);
            assertThat(service.runScenario("current-overdue-451").asExpected()).isFalse();

            decides(UNDERWRITER, null);
            assertThat(service.runScenario("clean-retail").asExpected()).isFalse();
        }

        @Test
        void anUnknownScenarioIsNotFound() {
            assertThatThrownBy(() -> service.runScenario("no-such-scenario"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("no-such-scenario");
        }
    }

    // ------------------------------------------------------------------------------------------------

    /** The reports passed to StopFactorService.check. */
    private record Reports(CustomReport customReport, IndividualReport individualReport, ConsolidationDto consolidation,
                           TotalDebtFinalResponse totalDebt, LocalDate requestDate) {
    }

    private Reports captured() {
        ArgumentCaptor<CustomReport> customReport = ArgumentCaptor.forClass(CustomReport.class);
        ArgumentCaptor<IndividualReport> individualReport = ArgumentCaptor.forClass(IndividualReport.class);
        ArgumentCaptor<ConsolidationDto> consolidation = ArgumentCaptor.forClass(ConsolidationDto.class);
        ArgumentCaptor<TotalDebtFinalResponse> totalDebt = ArgumentCaptor.forClass(TotalDebtFinalResponse.class);
        ArgumentCaptor<LocalDate> requestDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(stopFactorService).check(anyLong(), customReport.capture(), individualReport.capture(), consolidation.capture(),
                totalDebt.capture(), requestDate.capture());
        return new Reports(customReport.getValue(), individualReport.getValue(), consolidation.getValue(),
                totalDebt.getValue(), requestDate.getValue());
    }

    private void decides(StopFactorStatus status, InternalStatusCode code) {
        when(stopFactorService.check(anyLong(), any(), any(), any(), any(), any())).thenReturn(new StopFactorDecision(status, code));
    }

    private static TestCheckRequest request(Long scoringId, Long productId, Kib kib) {
        return new TestCheckRequest(scoringId, productId, DATE, kib, List.of());
    }

    /** A full KIB report without any data. */
    private static Kib kib() {
        return new Kib(0, 0, 0, null, 0, 0, null, null, null, false, null, null);
    }

    private static List<kg.bakai.spr.core.kib.dto.response.Contracts.Contract> contracts(CustomReport report) {
        return report.contracts().contractList().contracts();
    }

    private static ProductProjection product(long id, String name, ProductSegment segment) {
        ProductProjection product = mock(ProductProjection.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn(name);
        when(product.getSegment()).thenReturn(segment);
        return product;
    }

    private static StopFactorProjection projection(StopFactorStatus result, String name, String checkedValue) {
        StopFactorProjection projection = mock(StopFactorProjection.class);
        when(projection.getCheckResult()).thenReturn(result);
        when(projection.getStopFactorName()).thenReturn(name);
        when(projection.getCheckedValue()).thenReturn(checkedValue);
        return projection;
    }
}
