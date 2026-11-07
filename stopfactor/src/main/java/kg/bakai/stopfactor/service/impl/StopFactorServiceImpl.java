package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.StopFactorConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.service.StopFactorService;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.calculator.StopFactorCalculator;
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
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.CheckContextFactory;
import kg.bakai.stopfactor.report.ScoringReports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static kg.bakai.stopfactor.enums.StopFactorStatus.APPROVED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;

/**
 * Stop factor check of SPR, working with the versioned stop factors of changelog 1.3.
 * <ol>
 *     <li>the KIB / OB reports become a {@link CheckContext}: its facts (values, credit history, loan statuses,
 *     legacy counters) are made only when a calculator asks for them
 *     ({@link CheckContextFactory});</li>
 *     <li>the active stop factors that apply to the scoring's product are taken ({@link StopFactor#appliesTo});</li>
 *     <li>for each one its calculator measures the value and its conditions decide ({@link ConditionEvaluator});
 *     a value that cannot be measured is {@link #NO_DATA} and goes to the underwriter;</li>
 *     <li>any REJECTED rejects (451 if a current overdue stop factor rejected), otherwise any UNDERWRITER sends to
 *     the underwriter; the results replace earlier ones of the scoring.</li>
 * </ol>
 * Stop factors 1..34 as migrated give exactly the legacy results (LegacyStopFactorParityTest).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StopFactorServiceImpl implements StopFactorService {

    /**
     * Checked value of a stop factor that cannot be calculated from the reports (e.g. a period set in the console
     * that the report values do not cover). Such a stop factor sends the application to the underwriter.
     */
    public static final String NO_DATA = "NO_DATA";

    /** Author of the versions created through {@link #save} and {@link #saveCondition}. */
    static final String SPR_USER = "spr";

    /** A rejection by these metrics (legacy stop factors 9 and 10) gives code 451. */
    private static final Set<CalculatorType> CURRENT_OVERDUE_CALCULATORS =
            EnumSet.of(CalculatorType.CURRENT_OVERDUE_DAYS, CalculatorType.CURRENT_OVERDUE_AMOUNT);

    private final StopFactorRepository stopFactorRepository;
    private final StopFactorCheckRepository stopFactorCheckRepository;
    private final StopFactorMetricRepository stopFactorMetricRepository;
    private final StopFactorConsoleService stopFactorConsoleService;
    private final CheckContextFactory checkContextFactory;
    private final CalculatorRegistry calculatorRegistry;

    // ================================================================================================
    // StopFactorService
    // ================================================================================================

    @Override
    @Transactional
    public StopFactorDecision check(
            long scoringId,
            CustomReport customReport, IndividualReport individualReport,
            ConsolidationDto consolidationDto, TotalDebtFinalResponse totalDebt, LocalDate requestDate
    ) {
        Optional<ProductProjection> product = stopFactorRepository.findProductByScoringId(scoringId);
        Long productId = product.map(ProductProjection::getId).orElse(null);
        ProductSegment segment = product.map(ProductProjection::getSegment).orElse(ProductSegment.DEFAULT);
        CheckContext context = checkContextFactory.create(scoringId, productId,
                new ScoringReports(customReport, individualReport, consolidationDto, totalDebt, requestDate));

        List<StopFactor> stopFactors = stopFactorRepository.findCurrentActiveWithMetric().stream()
                .filter(stopFactor -> stopFactor.appliesTo(productId, segment))
                .toList();

        List<StopFactorCheck> checks = new ArrayList<>();
        for (StopFactor stopFactor : stopFactors) {
            StopFactorCalculator<?> calculator = calculatorRegistry.get(stopFactor.getMetric().getCalculator());
            for (StopFactorRule rule : stopFactor.getRules()) {
                if (!rule.isActive()) {
                    continue;
                }
                StopFactorCheck check = checkRule(rule, calculator, context);
                check.setProductId(productId);
                checks.add(check);
            }
        }

        // A repeated check of the same scoring replaces its previous results instead of duplicating them
        stopFactorCheckRepository.deleteByScoringId(scoringId);
        stopFactorCheckRepository.saveAll(checks);
        return decide(checks);
    }

    /**
     * Works for scorings checked before and after changelog 1.3: old check rows point to version 1 (the original
     * id), new rows to the version that was current; stopFactorId is always the original id.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StopFactorProjection> findByScoringId(Long scoringId) {
        return stopFactorCheckRepository.findByScoringIdForProjection(scoringId);
    }

    /**
     * Legacy saved {@code new StopFactor(name, description)}. Now a stop factor needs a metric, so the metric is
     * found by the name (its title or name template, e.g. "Количество активных споров КИБ"). Metrics with params
     * (periods, sources) need the console: POST /api/v1/admin/stop-factors.
     */
    @Override
    @Transactional
    public void save(String name, String description) {
        StopFactorMetric metric = stopFactorMetricRepository.findFirstByTitleOrNameTemplateOrderById(name, name)
                .orElseThrow(() -> new BusinessValidationException("Не найдена метрика для стоп-фактора «" + name
                        + "». Создайте стоп-фактор в консоли: выберите метрику и задайте параметры правил."));
        stopFactorConsoleService.create(new StopFactorRequest(metric.getId(), name, description, null, true, Set.of(), null,
                List.of(new RuleDto(Map.of(), List.of())), null), SPR_USER);
    }

    /**
     * Legacy saved the condition directly to the stop factor. Now conditions belong to a version, so the change
     * creates a new version: the old version keeps its conditions, and checks made with it still show them.
     * A condition with the same result (REJECTED / UNDERWRITER) is replaced by a legacy threshold (GT, or RANGE up
     * to REJECTED for UNDERWRITER), the other one is kept with its operator. An UNDERWRITER RANGE that ends at the
     * replaced REJECTED value moves with it, so the yellow zone still ends where the rejection starts.
     *
     * @param dto stopFactorId - id of the stop factor (its first version, ids 1..34 for the legacy ones)
     */
    @Override
    @Transactional
    public void saveCondition(StopFactorConditionDto dto) {
        StopFactorResponse current = stopFactorConsoleService.get(dto.stopFactorId());
        if (current.rules().size() != 1) {
            throw new BusinessValidationException("Стоп-фактор №" + dto.stopFactorId() + " состоит из " + current.rules().size()
                    + " правил, поэтому его условие нельзя изменить одним значением. Измените условие нужного правила в консоли.");
        }
        RuleDto rule = current.rules().getFirst();
        Optional<ConditionDto> currentRejected = rule.conditions().stream().filter(c -> c.result() == REJECTED).findFirst();
        List<ConditionDto> conditions = new ArrayList<>(rule.conditions().stream()
                .filter(c -> c.result() != dto.result())
                .map(c -> endsAtReplacedRejected(c, dto, currentRejected)
                        ? new ConditionDto(c.id(), c.result(), c.valueType(), c.value())
                        : c)
                .toList());
        conditions.add(new ConditionDto(null, dto.result(), dto.valueType(), dto.value()));

        stopFactorConsoleService.update(dto.stopFactorId(), new StopFactorRequest(
                current.metricId(),
                current.name(),
                current.description(),
                current.orderNumber(),
                current.active(),
                current.productIds(),
                current.segment(),
                List.of(new RuleDto(null, rule.name(), rule.params(), conditions, rule.active())),
                "Условие «" + dto.result().getLabel() + "» изменено из СПР"
        ), SPR_USER);
    }

    /** An UNDERWRITER RANGE up to the REJECTED value that saveCondition replaces. */
    private static boolean endsAtReplacedRejected(ConditionDto condition, StopFactorConditionDto replacement,
                                                  Optional<ConditionDto> currentRejected) {
        return replacement.result() == REJECTED
                && condition.operator() == ConditionOperator.RANGE
                && currentRejected.isPresent()
                && currentRejected.get().operator() == ConditionOperator.GT
                && new BigDecimal(condition.valueTo()).compareTo(new BigDecimal(currentRejected.get().value())) == 0;
    }

    // ================================================================================================
    // Check of one application
    // ================================================================================================

    /** The calculator measures the value with the params of the rule, its conditions decide. No value is NO_DATA. */
    private static StopFactorCheck checkRule(StopFactorRule rule, StopFactorCalculator<?> calculator,
                                             CheckContext context) {
        Optional<CheckedValue> value = calculator.bind(rule.getParams()).calculate(context);
        if (value.isEmpty()) {
            log.warn("Скоринг {}: нет данных для правила '{}' стоп-фактора {} ({} {}), заявка направлена андеррайтеру",
                    context.scoringId(), rule.getName(), rule.getStopFactor().getFactorId(), calculator.type(), rule.getParams());
            StopFactorCheck check = new StopFactorCheck(context.scoringId(), rule, NO_DATA);
            check.setResult(UNDERWRITER);
            return check;
        }
        ConditionEvaluator.Outcome outcome = ConditionEvaluator.apply(value.get(), rule.getConditions());
        StopFactorCheck check = new StopFactorCheck(context.scoringId(), rule, outcome.checkedValue());
        check.setResult(outcome.result());
        return check;
    }

    /** Any REJECTED rejects (451 if a current overdue stop factor rejected), otherwise any UNDERWRITER. */
    private static StopFactorDecision decide(List<StopFactorCheck> checks) {
        boolean rejected = checks.stream().anyMatch(check -> check.getResult() == REJECTED);
        if (rejected) {
            boolean currentOverdue = checks.stream().anyMatch(check -> check.getResult() == REJECTED
                    && CURRENT_OVERDUE_CALCULATORS.contains(check.getStopFactor().getMetric().getCalculator()));
            return new StopFactorDecision(REJECTED, currentOverdue ? InternalStatusCode._451 : null);
        }
        boolean underwriter = checks.stream().anyMatch(check -> check.getResult() == UNDERWRITER);
        return new StopFactorDecision(underwriter ? UNDERWRITER : APPROVED, null);
    }
}
