package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.stopfactor.service.StopFactorService;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.calculator.StopFactorCalculator;
import kg.bakai.stopfactor.condition.ConditionEvaluator;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCheck;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.repository.StopFactorCheckRepository;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.CheckContextFactory;
import kg.bakai.stopfactor.report.ScoringReports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static kg.bakai.stopfactor.enums.StopFactorStatus.APPROVED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;

/**
 * Stop factor check of SPR, working with the grouped stop factors of changelog 1.3.
 * <ol>
 *     <li>the KIB / OB reports become a {@link CheckContext}: its facts (values, credit history, loan statuses,
 *     legacy counters) are made only when a calculator asks for them
 *     ({@link CheckContextFactory});</li>
 *     <li>the current active groups are taken;</li>
 *     <li>in each of them every active stop factor that applies to the scoring's product and segment
 *     ({@link StopFactor#appliesTo}) is checked: its calculator measures the value and its conditions decide
 *     ({@link ConditionEvaluator}); a value that cannot be measured is {@link #NO_DATA} and goes to the
 *     underwriter;</li>
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

    /** A rejection by these metrics (legacy stop factors 9 and 10) gives code 451. */
    private static final Set<CalculatorType> CURRENT_OVERDUE_CALCULATORS =
            EnumSet.of(CalculatorType.CURRENT_OVERDUE_DAYS, CalculatorType.CURRENT_OVERDUE_AMOUNT);

    private final StopFactorGroupRepository groupRepository;
    private final StopFactorCheckRepository stopFactorCheckRepository;
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
        Optional<ProductProjection> product = groupRepository.findProductByScoringId(scoringId);
        Long productId = product.map(ProductProjection::getId).orElse(null);
        ProductSegment segment = product.map(ProductProjection::getSegment).orElse(ProductSegment.DEFAULT);
        CheckContext context = checkContextFactory.create(scoringId, productId,
                new ScoringReports(customReport, individualReport, consolidationDto, totalDebt, requestDate));

        List<StopFactorCheck> checks = new ArrayList<>();
        for (StopFactorGroup group : groupRepository.findCurrentActiveWithFactorsAndMetric()) {
            for (StopFactor factor : group.getFactors()) {
                if (!factor.isActive() || !factor.appliesTo(productId, segment)) {
                    continue;
                }
                StopFactorCalculator<?> calculator = calculatorRegistry.get(factor.getMetric().getCalculator());
                StopFactorCheck check = checkFactor(factor, calculator, context);
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
     * Works for scorings checked before and after changelog 1.3: a check row points to the stop factor it was
     * evaluated with (a legacy check keeps its legacy stop factor id), and stopFactorId is always the id of the group.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StopFactorProjection> findByScoringId(Long scoringId) {
        return stopFactorCheckRepository.findByScoringIdForProjection(scoringId);
    }

    // ================================================================================================
    // Check of one application
    // ================================================================================================

    /**
     * The calculator measures the value with the params of the stop factor, its conditions decide. No value is NO_DATA:
     * it goes to the underwriter, unless the stop factor has no conditions and so decides nothing.
     */
    private static StopFactorCheck checkFactor(StopFactor factor, StopFactorCalculator<?> calculator,
                                               CheckContext context) {
        Optional<CheckedValue> value = calculator.bind(factor.getParams()).calculate(context);
        if (value.isEmpty()) {
            StopFactorCheck check = new StopFactorCheck(context.scoringId(), factor, NO_DATA);
            if (!factor.getConditions().isEmpty()) {
                log.warn("Скоринг {}: нет данных для стоп-фактора '{}' группы {} ({} {}), заявка направлена андеррайтеру",
                        context.scoringId(), factor.getName(), factor.getGroup().getGroupId(), calculator.type(), factor.getParams());
                check.setResult(UNDERWRITER);
            }
            return check;
        }
        ConditionEvaluator.Outcome outcome = ConditionEvaluator.apply(value.get(), factor.getConditions());
        StopFactorCheck check = new StopFactorCheck(context.scoringId(), factor, outcome.checkedValue());
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
