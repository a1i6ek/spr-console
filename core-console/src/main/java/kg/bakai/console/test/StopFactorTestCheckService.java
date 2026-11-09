package kg.bakai.console.test;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.console.test.TestCheckRequest.Kib;
import kg.bakai.console.test.TestCheckRequest.KibPart;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.GeneralInfoDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.LoanDto;
import kg.bakai.spr.core.kib.dto.enums.LoanPhase;
import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.spr.core.kib.dto.enums.RoleOfClient;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.CalendarItem;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract.PaymentCalendarList;
import kg.bakai.spr.core.kib.dto.response.Contracts.Contract;
import kg.bakai.spr.core.kib.dto.response.Contracts.ContractList;
import kg.bakai.spr.core.kib.dto.response.Contracts;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.TotalDebtReportData;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.service.StopFactorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LOCAL TESTING ONLY. Builds the KIB / OB reports SPR would pass from a short request, imitates missing data
 * and calls {@link StopFactorService#check} exactly as SPR does. The results are saved like real ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stop-factor.test-check.enabled", havingValue = "true")
public class StopFactorTestCheckService {

    // Test scorings without an id get negative ids, so they never clash with real scorings
    private final AtomicLong nextTestScoringId = new AtomicLong(-1_000_000_000L - System.currentTimeMillis() % 1_000_000_000L);

    private final StopFactorService stopFactorService;
    private final StopFactorRepository stopFactorRepository;
    private final StopFactorCheckRepository stopFactorCheckRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<TestCheckScenario> scenarios() {
        return TestCheckScenarios.ALL;
    }

    @Transactional
    public TestCheckScenarioResult runScenario(String name) {
        TestCheckScenario scenario = TestCheckScenarios.ALL.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Сценарий '" + name + "' не найден, список: GET /api/v1/admin/test/stop-factor-check/scenarios"));
        TestCheckResponse result = check(scenario.request());
        boolean asExpected = result.decision() == scenario.expectedDecision()
                && result.internalStatusCode() == scenario.expectedInternalStatusCode();
        return new TestCheckScenarioResult(scenario.name(), scenario.description(), scenario.expectedDecision(),
                scenario.expectedInternalStatusCode(), asExpected, result);
    }

    @Transactional
    public TestCheckResponse check(TestCheckRequest request) {
        List<String> imitated = new ArrayList<>();
        long scoringId = request.scoringId() != null ? request.scoringId() : nextTestScoringId.getAndDecrement();
        if (request.productId() != null) {
            linkScoringToProduct(scoringId, request.productId());
        }

        Optional<ProductProjection> product = stopFactorRepository.findProductByScoringId(scoringId);
        ProductSegment segment = product.map(ProductProjection::getSegment).orElse(ProductSegment.DEFAULT);
        if (product.isEmpty()) {
            imitated.add("Скоринг " + scoringId + " без продукта: ограничения по продуктам не действуют, сегмент " + segment + " по умолчанию");
        }

        LocalDate requestDate = request.requestDate() != null ? request.requestDate() : LocalDate.now();
        Kib kib = request.kib();
        CustomReport customReport = null;
        IndividualReport individualReport = null;
        ConsolidationDto consolidation = null;
        if (kib == null) {
            imitated.add("Нет отчёта КИБ: проверяются только данные ОБ, значения КИБ и история просрочек = 0");
        } else {
            Set<KibPart> missing = kib.missingParts() != null ? kib.missingParts() : Set.of();
            if (!missing.contains(KibPart.CUSTOM_REPORT)) {
                customReport = customReport(kib, requestDate, imitated);
            }
            if (!missing.contains(KibPart.INDIVIDUAL_REPORT)) {
                individualReport = new IndividualReport(
                        new GeneralInfoDto(kib.activeDisputes(), kib.unjustifiedDisputes(), kib.bureauRequestsLast7Days()),
                        loans(kib));
            }
            if (!missing.contains(KibPart.CONSOLIDATION)) {
                consolidation = new ConsolidationDto(Objects.requireNonNullElse(kib.overdueAmount(), BigDecimal.ZERO),
                        kib.newCreditsLast30Days(), kib.openCredits());
            }
            if (!missing.isEmpty()) {
                imitated.add("Неполный отчёт КИБ, нет " + new TreeSet<>(missing)
                        + ": сервис считает, что отчёта КИБ нет, проверяются только данные ОБ");
            }
        }

        List<TotalDebtReportData> obCredits = request.obCredits() == null ? List.of() : request.obCredits().stream()
                .map(c -> new TotalDebtReportData(c.overdueDays(), c.overdueSum(), c.lastChangeDate()))
                .toList();
        if (obCredits.isEmpty()) {
            imitated.add("Нет кредитов в отчёте ОБ");
        }

        StopFactorDecision decision = stopFactorService.check(scoringId, customReport, individualReport, consolidation,
                new TotalDebtFinalResponse(new ArrayList<>(obCredits)), requestDate);
        stopFactorCheckRepository.flush();
        List<StopFactorProjection> checks = stopFactorService.findByScoringId(scoringId);
        log.info("Тестовая проверка скоринга {}: {} {}", scoringId, decision.status(), decision.internalStatusCode());

        return new TestCheckResponse(
                scoringId,
                product.map(ProductProjection::getId).orElse(null),
                product.map(ProductProjection::getName).orElse(null),
                segment,
                decision.status(),
                decision.internalStatusCode(),
                imitated,
                checks.stream()
                        .filter(c -> c.getCheckResult() != StopFactorStatus.APPROVED)
                        .map(c -> c.getCheckResult() + ": " + c.getStopFactorName() + " (" + c.getCheckedValue() + ")")
                        .toList(),
                checks);
    }

    /**
     * Contracts of the custom report: the raw ones of the request, plus one main debtor contract per requested
     * overdue month with a single calendar entry in that month, so the calculator returns exactly that month.
     */
    private static CustomReport customReport(Kib kib, LocalDate requestDate, List<String> imitated) {
        List<Contract> contracts = new ArrayList<>();
        if (kib.contracts() != null) {
            kib.contracts().forEach(c -> {
                List<CalendarItem> calendar = c.calendar() == null ? List.of() : c.calendar().stream()
                        .map(e -> new CalendarItem(e.date().atStartOfDay(), e.pastDueDays(), e.maxPastDueDays()))
                        .toList();
                LocalDateTime startDate = c.startDate() != null ? c.startDate().atStartOfDay() : firstMonth(calendar);
                contracts.add(contract(c.role(), startDate, calendar));
            });
        }
        if (kib.overdueMonths() != null) {
            kib.overdueMonths().forEach(e -> {
                LocalDateTime month = requestDate.minusMonths(e.monthsAgo() - 1L).atStartOfDay();
                contracts.add(contract(RoleOfClient.MainDebtor, month, List.of(new CalendarItem(month, 0, e.maxOverdueDays()))));
            });
        }
        if (kib.calendarsUnavailable()) {
            List<Contract> withoutCalendars = contracts.stream().map(c -> contract(c.roleOfClient(), c.startDate(), List.of())).toList();
            contracts.clear();
            contracts.addAll(withoutCalendars.isEmpty() ? List.of(contract(RoleOfClient.MainDebtor, null, List.of())) : withoutCalendars);
            imitated.add("В отчёте КИБ нет платежных календарей: месяцы просрочки не найдены, правила по просрочкам "
                    + "читают 17 старых счётчиков, диапазоны без счётчика дают NO_DATA, заявка уходит андеррайтеру");
        }
        return new CustomReport(new Contracts(new ContractList(contracts)), kib.legacyOverdue());
    }

    private static Contract contract(RoleOfClient role, LocalDateTime startDate, List<CalendarItem> calendar) {
        return new Contract(Objects.requireNonNullElse(role, RoleOfClient.MainDebtor), startDate, new PaymentCalendarList(calendar));
    }

    private static LocalDateTime firstMonth(List<CalendarItem> calendar) {
        return calendar.stream().map(CalendarItem::date).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
    }

    private static List<LoanDto> loans(Kib kib) {
        if (kib.loans() == null) {
            return List.of();
        }
        return kib.loans().stream()
                .map(l -> new LoanDto(
                        Objects.requireNonNullElse(l.role(), RoleOfClient.MainDebtor),
                        Objects.requireNonNullElse(l.status(), LoanStatus.Open),
                        l.restructuringsLast12Months(),
                        l.restructuringsFrom12To24Months(),
                        l.daysDelay(),
                        Objects.requireNonNullElse(l.phase(), LoanPhase.Open),
                        l.delayAmount(),
                        l.overdueInterest()))
                .toList();
    }

    // public.scoring belongs to SPR; here the test application needs a scoring row to find its product
    private void linkScoringToProduct(long scoringId, long productId) {
        if (stopFactorRepository.findProductsByIds(List.of(productId)).isEmpty()) {
            throw new NotFoundException("Продукт " + productId + " не найден");
        }
        jdbcTemplate.update("""
                INSERT INTO public.scoring (id, created_date, product_id) VALUES (?, now(), ?)
                ON CONFLICT (id) DO UPDATE SET product_id = EXCLUDED.product_id
                """, scoringId, productId);
    }
}
