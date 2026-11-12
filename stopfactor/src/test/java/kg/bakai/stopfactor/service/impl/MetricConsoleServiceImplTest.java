package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.KibRefusalStatusesCalculator;
import kg.bakai.stopfactor.calculator.OverdueBucketCountCalculator;
import kg.bakai.stopfactor.calculator.RestructuringCountCalculator;
import kg.bakai.stopfactor.config.CalculatorConfig;
import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.dto.MetricParam;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.validation.StopFactorValidation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static kg.bakai.stopfactor.enums.CalculatorType.OVERDUE_BUCKET_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests of the metric console service, no database: create, change, delete and the calculator list. */
class MetricConsoleServiceImplTest {

    private final StopFactorMetricRepository metricRepository = mock(StopFactorMetricRepository.class);
    private final StopFactorGroupRepository groupRepository = mock(StopFactorGroupRepository.class);

    private final List<StopFactorMetric> metrics = new ArrayList<>();
    private long nextId = 1;

    private MetricConsoleServiceImpl service;

    @BeforeEach
    void setUp() {
        CalculatorConfig config = new CalculatorConfig();
        CalculatorRegistry registry = new CalculatorRegistry(List.of(
                config.kibActiveDisputesCalculator(),
                config.kibUnjustifiedDisputesCalculator(),
                config.currentOverdueDaysCalculator(),
                config.currentOverdueAmountCalculator(),
                config.openCreditsCountCalculator(),
                config.bureauRequestsCountCalculator(),
                config.newCreditsCountCalculator(),
                new KibRefusalStatusesCalculator(),
                new OverdueBucketCountCalculator(),
                new RestructuringCountCalculator()));
        service = new MetricConsoleServiceImpl(metricRepository, registry,
                new StopFactorValidation(registry, groupRepository));

        when(metricRepository.findAllOrdered()).thenAnswer(invocation -> List.copyOf(metrics));
        when(metricRepository.findById(anyLong())).thenAnswer(invocation -> metrics.stream()
                .filter(m -> m.getId().equals(invocation.getArgument(0)))
                .findFirst());
        // like the database: a saved metric gets its id
        when(metricRepository.saveAndFlush(any(StopFactorMetric.class))).thenAnswer(invocation -> {
            StopFactorMetric metric = invocation.getArgument(0);
            if (metric.getId() == null) {
                metric.setId(nextId++);
                metrics.add(metric);
            }
            return metric;
        });
    }

    /** A metric as changelogs create it: OVERDUE_BUCKET_COUNT with every param. */
    private static StopFactorMetric metric(long id, String title) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setId(id);
        metric.setTitle(title);
        metric.setCalculator(OVERDUE_BUCKET_COUNT);
        metric.setParams(List.of(
                new MetricParam("periodFromMonth", "Период с (месяц)", true),
                new MetricParam("periodToMonth", "Период по (месяц)", true),
                new MetricParam("minOverdueDays", "Просрочка от (дней)", true),
                new MetricParam("maxOverdueDays", "Просрочка до (дней)", false)));
        metric.setActive(true);
        return metric;
    }

    private static MetricDto request(String title, List<MetricParam> params) {
        return new MetricDto(null, title, OVERDUE_BUCKET_COUNT, null, params, null, null, null);
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("getAll: every metric in id order, the calculator named")
        void getAll() {
            metrics.add(metric(2, "Вторая"));
            metrics.add(metric(1, "Первая"));

            List<MetricDto> all = service.getAll();

            assertThat(all).extracting(MetricDto::id).containsExactly(2L, 1L);
            assertThat(all.get(0).calculatorName()).isEqualTo(OVERDUE_BUCKET_COUNT.getName());
            assertThat(all.get(0).params()).hasSize(4);
        }

        @Test
        @DisplayName("get: an unknown metric - not found")
        void getNotFound() {
            assertThatThrownBy(() -> service.get(999))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("не найдена");
        }

        @Test
        @DisplayName("getCalculators: every calculator with its params, value types and operators")
        void calculators() {
            List<CalculatorDto> calculators = service.getCalculators();

            assertThat(calculators).hasSize(CalculatorType.values().length);
            CalculatorDto bucket = calculators.stream()
                    .filter(c -> c.type() == OVERDUE_BUCKET_COUNT)
                    .findFirst().orElseThrow();
            assertThat(bucket.valueKind()).isEqualTo(ValueKind.NUMBER);
            assertThat(bucket.allowedOperators()).extracting(CalculatorDto.OperatorOption::code)
                    .contains(ConditionOperator.GT, ConditionOperator.RANGE);
            assertThat(bucket.allowedConditionValueTypes()).containsExactlyInAnyOrderElementsOf(ValueKind.NUMBER.conditionTypes());
            assertThat(bucket.params()).extracting(CalculatorDto.CalculatorParam::key)
                    .containsExactly("periodFromMonth", "periodToMonth", "minOverdueDays", "maxOverdueDays");
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("create: without params it gets every param of the calculator, active by default")
        void createDefaultsParams() {
            MetricDto created = service.create(request("Просрочка", null));

            assertThat(created.id()).isNotNull();
            assertThat(created.active()).isTrue();
            assertThat(created.params()).extracting(MetricParam::key)
                    .containsExactly("periodFromMonth", "periodToMonth", "minOverdueDays", "maxOverdueDays");
            assertThat(created.params()).extracting(MetricParam::required)
                    .containsExactly(true, true, true, false);
        }

        @Test
        @DisplayName("create: the title is required")
        void titleRequired() {
            assertThatThrownBy(() -> service.create(request("  ", null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("название метрики")));
        }

        @Test
        @DisplayName("create: a metric with the same title is refused")
        void duplicateTitle() {
            metrics.add(metric(1, "Просрочка"));

            assertThatThrownBy(() -> service.create(request("Просрочка", null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("уже есть")));
        }

        @Test
        @DisplayName("create: a param the calculator does not have is refused")
        void unknownParam() {
            assertThatThrownBy(() -> service.create(request("Просрочка",
                    List.of(new MetricParam("days", "Дней", true)))))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("не используется калькулятором")));
        }

        @Test
        @DisplayName("create: a required calculator param may not be left optional")
        void requiredParamKeptRequired() {
            assertThatThrownBy(() -> service.create(request("Просрочка", List.of(
                    new MetricParam("periodFromMonth", "Период с (месяц)", false),
                    new MetricParam("periodToMonth", "Период по (месяц)", true),
                    new MetricParam("minOverdueDays", "Просрочка от (дней)", true)))))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("обязателен для калькулятора")));
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("update: the title and the active flag change, the calculator stays")
        void updateTitleAndActive() {
            metrics.add(metric(1, "Старое имя"));

            MetricDto updated = service.update(1, new MetricDto(null, "Новое имя", OVERDUE_BUCKET_COUNT,
                    null, null, false, null, null));

            assertThat(updated.title()).isEqualTo("Новое имя");
            assertThat(updated.active()).isFalse();
            assertThat(updated.calculator()).isEqualTo(OVERDUE_BUCKET_COUNT);
            assertThat(metrics.get(0).getTitle()).isEqualTo("Новое имя");
        }

        @Test
        @DisplayName("update: params left out are kept")
        void updateKeepsParams() {
            metrics.add(metric(1, "Просрочка"));

            MetricDto updated = service.update(1, request("Просрочка", null));

            assertThat(updated.params()).hasSize(4);
        }

        @Test
        @DisplayName("update: the calculator cannot change")
        void calculatorImmutable() {
            metrics.add(metric(1, "Просрочка"));

            assertThatThrownBy(() -> service.update(1, new MetricDto(null, "Просрочка", CalculatorType.NEW_CREDITS_COUNT,
                    null, null, null, null, null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("Калькулятор метрики нельзя изменить")));
        }

        @Test
        @DisplayName("update: refused when a current stop factor would not fit the new params")
        void refusesBrokenStopFactors() {
            metrics.add(metric(1, "Просрочка"));
            // a factor that sets the optional param too
            StopFactorGroup group = new StopFactorGroup();
            group.setName("Комитет");
            StopFactor fitting = group.addFactor();
            fitting.setName("Просрочка 30-60");
            fitting.setMetric(metrics.get(0));
            fitting.setParams(Map.of("periodFromMonth", 1, "periodToMonth", 6,
                    "minOverdueDays", 30, "maxOverdueDays", 60));
            when(metricRepository.findCurrentStopFactors(1L)).thenReturn(List.of(fitting));

            // dropping the optional param leaves the factor with a param the metric does not have
            assertThatThrownBy(() -> service.update(1, request("Просрочка", List.of(
                    new MetricParam("periodFromMonth", "Период с (месяц)", true),
                    new MetricParam("periodToMonth", "Период по (месяц)", true),
                    new MetricParam("minOverdueDays", "Просрочка от (дней)", true)))))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("не подходит под новые параметры")));
        }

        @Test
        @DisplayName("update: fine while no current stop factor uses the metric")
        void updateWithoutStopFactors() {
            metrics.add(metric(1, "Просрочка"));
            when(metricRepository.findCurrentStopFactors(1L)).thenReturn(List.of());

            MetricDto updated = service.update(1, request("Просрочка", List.of(
                    new MetricParam("periodFromMonth", "Период с (месяц)", true),
                    new MetricParam("periodToMonth", "Период по (месяц)", true),
                    new MetricParam("minOverdueDays", "Просрочка от (дней)", true))));

            assertThat(updated.params()).extracting(MetricParam::key)
                    .containsExactly("periodFromMonth", "periodToMonth", "minOverdueDays");
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("delete: a metric no stop factor uses")
        void deleteUnused() {
            metrics.add(metric(1, "Просрочка"));
            when(metricRepository.isUsed(1L)).thenReturn(false);

            service.delete(1);

            verify(metricRepository).delete(metrics.get(0));
        }

        @Test
        @DisplayName("delete: refused while a stop factor uses the metric")
        void refusesUsedMetric() {
            metrics.add(metric(1, "Просрочка"));
            when(metricRepository.isUsed(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(1))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("используют стоп-факторы")));
        }
    }
}
