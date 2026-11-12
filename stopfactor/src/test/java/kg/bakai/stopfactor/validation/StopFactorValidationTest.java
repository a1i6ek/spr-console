package kg.bakai.stopfactor.validation;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.StopFactorCalculator;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.dto.MetricParam;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.exception.InvalidParamsException;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static kg.bakai.stopfactor.enums.ConditionOperator.*;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StopFactorValidationTest {

    /** SPR's own segment_type values: BUSINESS is MSB, every other one RETAIL. */
    private static final Map<Long, ProductProjection> PRODUCTS = Map.of(
            1L, product(1, "Online кредит", "RETAIL"),
            2L, product(2, "Тумар", "RETAIL"),
            5L, product(5, "Бизнес лимит", "BUSINESS"),
            6L, product(6, "Онлайн ИФЦ", "IFC"));

    private static final StopFactorMetric OVERDUE = metric(10, CalculatorType.OVERDUE_BUCKET_COUNT,
            new MetricParam("periodFromMonth", "Период с (месяц)", true),
            new MetricParam("maxOverdueDays", "Максимальная просрочка до (дней)", false));
    private static final StopFactorMetric STATUSES = metric(1, CalculatorType.KIB_REFUSAL_STATUSES);

    private final CalculatorRegistry calculatorRegistry = mock(CalculatorRegistry.class);
    private final StopFactorGroupRepository repository = mock(StopFactorGroupRepository.class);
    private final StopFactorValidation validator = new StopFactorValidation(calculatorRegistry, repository);

    private final List<String> errors = new ArrayList<>();

    @BeforeEach
    void calculators() {
        calculator(CalculatorType.OVERDUE_BUCKET_COUNT, ValueKind.NUMBER);
        calculator(CalculatorType.KIB_REFUSAL_STATUSES, ValueKind.LIST);
    }

    @Nested
    class Params {

        @Test
        void everyKeyMustBeAParamOfTheMetricAndRequiredOnesSet() {
            validator.validate(number(Map.of("days", 7), INTEGER, GT, "8", null, null, null, null), OVERDUE, errors);

            assertThat(errors).containsExactly(
                    "Параметр «days» не задан в метрике «Просрочки».",
                    "Укажите «Период с (месяц)».");
        }

        @Test
        void valuesAreCheckedByTheCalculator() {
            when(calculatorRegistry.get(CalculatorType.OVERDUE_BUCKET_COUNT).normalizeParams(any()))
                    .thenThrow(new InvalidParamsException(List.of("«Период с (месяц)» должно быть не меньше 1.")));

            validator.validate(number(Map.of("periodFromMonth", 0), INTEGER, GT, "8", null, null, null, null), OVERDUE, errors);

            assertThat(errors).containsExactly("«Период с (месяц)» должно быть не меньше 1.");
        }
    }

    @Nested
    class Thresholds {

        @Test
        void aStopFactorWithoutConditionsIsValid() {
            StopFactorDto valid = valid(number(INTEGER, null, null, null, null, null, null));

            assertThat(valid.conditions()).isEmpty();
            assertThat(valid.active()).isTrue();
            assertThat(valid.metricId()).isEqualTo(10L);
        }

        @Test
        void valuesAreNormalized() {
            StopFactorDto valid = valid(number(INTEGER, GT, " 08 ", null, RANGE, "5", "8"));

            assertThat(valid.conditions()).containsExactly(
                    new ConditionDto(StopFactorStatus.REJECTED, GT, "8", null, INTEGER),
                    new ConditionDto(StopFactorStatus.UNDERWRITER, RANGE, "5", "8", INTEGER));
        }

        @Test
        void aValueNeedsAnOperatorAndAType() {
            assertErrors(number(INTEGER, null, "8", null, null, null, null), "Условие «Отказ»: укажите оператор.");
            assertErrors(number(null, GT, "8", null, null, null, null), "Условие «Отказ»: укажите тип значения.");
        }

        @Test
        void rangeNeedsAnUpperBoundAboveTheValueAndOtherOperatorsNone() {
            assertErrors(number(INTEGER, null, null, null, RANGE, "5", null),
                    "Условие «Андеррайтер»: укажите верхнюю границу для оператора «> и <=».");
            assertErrors(number(INTEGER, null, null, null, RANGE, "5", "5"),
                    "Условие «Андеррайтер»: верхняя граница (5) должна быть больше нижней (5).");
            assertErrors(number(INTEGER, GT, "8", "10", null, null, null),
                    "Условие «Отказ»: верхняя граница задаётся только для операторов «> и <=» и «>= и <=».");
        }

        @Test
        void anUnderwriterThresholdThatCanNeverFireIsRefused() {
            assertErrors(number(INTEGER, GT, "5", null, GT, "8", null),
                    "Условие «Андеррайтер» (x > 8) никогда не сработает: при всех этих значениях уже срабатывает «Отказ» "
                            + "(x > 5), который проверяется первым.");
        }

        @Test
        void operatorsAndTypesMustFitTheCalculator() {
            assertErrors(number(ARRAY, IN, "WrittenOff", null, null, null, null),
                    "Условие «Отказ»: тип значения «Список статусов» не подходит для калькулятора. Допустимые типы: "
                            + "«Целое число», «Число».");
            assertErrors(statuses(GT, "WrittenOff", null, null),
                    "Условие «Отказ»: оператор «>» не подходит для калькулятора. Допустимые операторы: «in».");
        }

        @Test
        void aStatusMayNotBeInBothThresholds() {
            assertErrors(statuses(IN, "WrittenOff, Sold", IN, "Sold"),
                    "Статусы sold указаны в обоих условиях. Для них всегда срабатывает «Отказ», уберите их из условия «Андеррайтер».");
        }

        @Test
        void eachResultOnce() {
            StopFactorDto twice = new StopFactorDto(null, 10L, null, "Просрочки", null, null, null, Map.of("periodFromMonth", 1),
                    List.of(new ConditionDto(StopFactorStatus.REJECTED, GT, "5", null, INTEGER),
                            new ConditionDto(StopFactorStatus.REJECTED, GT, "8", null, INTEGER),
                            new ConditionDto(StopFactorStatus.APPROVED, GT, "1", null, INTEGER)),
                    Set.of(), null);

            assertErrors(twice,
                    "Условие «Отказ» указано несколько раз: у стоп-фактора может быть только одно такое условие.",
                    "Результат условия должен быть «Отказ» или «Андеррайтер».");
        }

        @Test
        void aNameIsRequired() {
            StopFactorDto noName = new StopFactorDto(null, 10L, null, " ", null, null, null, Map.of("periodFromMonth", 1),
                    List.of(), Set.of(), null);

            assertErrors(noName, "Укажите название стоп-фактора.");
        }
    }

    @Nested
    class Products {

        @Test
        void withoutProductsTheSegmentIsKeptAndNothingIsRead() {
            assertThat(valid(limited(MSB, Set.of())).segment()).isEqualTo(MSB);
            assertThat(valid(limited(null, null)).segment()).isNull();
            verifyNoInteractions(repository);
        }

        @Test
        void productsOfTheSegmentAreAcceptedAndIfcIsRetail() {
            productsExist();

            assertThat(valid(limited(RETAIL, Set.of(1L, 2L, 6L))).segment()).isEqualTo(RETAIL);
        }

        @Test
        void aProductOfAnotherSegmentIsRejectedByName() {
            productsExist();

            assertErrors(limited(RETAIL, Set.of(1L, 5L)),
                    "Продукт «Бизнес лимит» (id 5) относится к сегменту MSB, а стоп-фактор к сегменту RETAIL.");
        }

        @Test
        void withoutSegmentItIsTheSegmentAllProductsShare() {
            productsExist();

            assertThat(valid(limited(null, Set.of(1L, 2L))).segment()).isEqualTo(RETAIL);
            assertThat(valid(limited(null, Set.of(5L))).segment()).isEqualTo(MSB);
            assertThat(valid(limited(null, Set.of(1L, 5L))).segment()).isNull();
        }

        @Test
        void unknownProductsAreReportedInOrder() {
            productsExist();

            assertErrors(limited(RETAIL, Set.of(1L, 99L, 42L)), "Продукт с id 42 не найден.", "Продукт с id 99 не найден.");
        }
    }

    // ------------------------------------------------------------------------------------------------

    private StopFactorDto valid(StopFactorDto request) {
        StopFactorMetric metric = request.metricId() == 1L ? STATUSES : OVERDUE;
        StopFactorDto valid = validator.validate(request, metric, errors);
        assertThat(errors).isEmpty();
        assertThat(valid.summary()).isNotNull();
        return valid;
    }

    private void assertErrors(StopFactorDto request, String... expected) {
        StopFactorMetric metric = request.metricId() == 1L ? STATUSES : OVERDUE;
        assertThat(validator.validate(request, metric, errors)).isNull();
        assertThat(errors).containsExactly(expected);
        errors.clear();
    }

    private void calculator(CalculatorType type, ValueKind kind) {
        StopFactorCalculator<?> calculator = mock(StopFactorCalculator.class);
        when(calculator.valueKind()).thenReturn(kind);
        when(calculator.normalizeParams(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(calculatorRegistry.get(type)).thenAnswer(invocation -> calculator);
    }

    private static StopFactorDto number(StopFactorValueTypes type, ConditionOperator rejected, String rejectedValue,
                                        String rejectedValueTo, ConditionOperator underwriter, String underwriterValue,
                                        String underwriterValueTo) {
        return number(Map.of("periodFromMonth", 1), type, rejected, rejectedValue, rejectedValueTo, underwriter,
                underwriterValue, underwriterValueTo);
    }

    private static StopFactorDto number(Map<String, Object> params, StopFactorValueTypes type, ConditionOperator rejected,
                                        String rejectedValue, String rejectedValueTo, ConditionOperator underwriter,
                                        String underwriterValue, String underwriterValueTo) {
        return new StopFactorDto(null, 10L, null, "Просрочки", null, null, null, params,
                conditions(type, rejected, rejectedValue, rejectedValueTo, underwriter, underwriterValue, underwriterValueTo), Set.of(),
                null);
    }

    private static StopFactorDto statuses(ConditionOperator rejected, String rejectedValue,
                                          ConditionOperator underwriter, String underwriterValue) {
        return new StopFactorDto(null, 1L, null, "Статусы", null, null, null, Map.of(),
                conditions(ARRAY, rejected, rejectedValue, null, underwriter, underwriterValue, null), Set.of(), null);
    }

    private static StopFactorDto limited(ProductSegment segment, Set<Long> productIds) {
        return new StopFactorDto(null, 10L, null, "Просрочки", null, null, segment, Map.of("periodFromMonth", 1), List.of(),
                productIds, null);
    }

    /** The conditions a test sends: one of a result when its operator or value is given. */
    private static List<ConditionDto> conditions(StopFactorValueTypes type, ConditionOperator rejected, String rejectedValue,
                                                 String rejectedValueTo, ConditionOperator underwriter,
                                                 String underwriterValue, String underwriterValueTo) {
        List<ConditionDto> conditions = new ArrayList<>();
        if (rejected != null || rejectedValue != null) {
            conditions.add(new ConditionDto(StopFactorStatus.REJECTED, rejected, rejectedValue, rejectedValueTo, type));
        }
        if (underwriter != null || underwriterValue != null) {
            conditions.add(new ConditionDto(StopFactorStatus.UNDERWRITER, underwriter, underwriterValue, underwriterValueTo, type));
        }
        return conditions;
    }

    private static StopFactorMetric metric(long id, CalculatorType calculator, MetricParam... params) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setId(id);
        metric.setTitle(id == 10 ? "Просрочки" : "Отказные статусы");
        metric.setCalculator(calculator);
        metric.setParams(List.of(params));
        return metric;
    }

    /** findProductsByIds returns the known products among the requested ids, as the native query does. */
    @SuppressWarnings("unchecked")
    private void productsExist() {
        when(repository.findProductsByIds(anyCollection())).thenAnswer(invocation ->
                ((Collection<Long>) invocation.getArgument(0)).stream()
                        .filter(PRODUCTS::containsKey)
                        .map(PRODUCTS::get)
                        .toList());
    }

    private static ProductProjection product(long id, String name, String segmentType) {
        return new ProductProjection() {
            public Long getId() { return id; }
            public String getName() { return name; }
            public String getDescription() { return name; }
            public String getSegmentType() { return segmentType; }
        };
    }
}
