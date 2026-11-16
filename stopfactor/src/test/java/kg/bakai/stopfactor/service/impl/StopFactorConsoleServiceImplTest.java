package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.KibRefusalStatusesCalculator;
import kg.bakai.stopfactor.calculator.OverdueBucketCountCalculator;
import kg.bakai.stopfactor.calculator.RestructuringCountCalculator;
import kg.bakai.stopfactor.config.CalculatorConfig;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.MetricParam;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;
import kg.bakai.stopfactor.validation.StopFactorValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static kg.bakai.stopfactor.enums.ConditionOperator.GT;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the console service, no database: any change makes the next box with copies of all stop factors, the
 * changed one with its next version. Boxes and stop factors are real entities in memory, repositories are mocks, the
 * validator is real.
 */
class StopFactorConsoleServiceImplTest {

    private static final LocalDateTime THEN = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final StopFactorRepository factorRepository = mock(StopFactorRepository.class);
    private final StopFactorGroupRepository groupRepository = mock(StopFactorGroupRepository.class);
    private final StopFactorMetricRepository metricRepository = mock(StopFactorMetricRepository.class);

    /** Every box, like stop_factor_group: closed ones stay for the checks made with their stop factors. */
    private final List<StopFactorGroup> boxes = new ArrayList<>();
    private final List<StopFactorMetric> metrics = new ArrayList<>();
    private final List<Object[]> checkCounts = new ArrayList<>();
    private long nextId = 100;
    private long nextFactorId = 1000;

    private StopFactorConsoleServiceImpl service;

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
        service = new StopFactorConsoleServiceImpl(factorRepository, groupRepository, metricRepository,
                new StopFactorValidation(registry, factorRepository));

        metrics.add(metric(10, "Просроченная задолженность", true));
        metrics.add(metric(11, "Отключённая метрика", false));
        StopFactorGroup first = StopFactorGroup.first(THEN, "Стоп-факторы SPR");
        first.setId(1L);
        boxes.add(first);

        when(metricRepository.findById(anyLong())).thenAnswer(i -> metrics.stream()
                .filter(m -> m.getId().equals(i.getArgument(0))).findFirst());
        when(groupRepository.lock()).thenReturn(Optional.of(1L));
        when(groupRepository.findCurrent()).thenAnswer(i -> boxes.stream().filter(StopFactorGroup::isCurrent).findFirst());
        when(groupRepository.findByVersionNumber(anyInt())).thenAnswer(i -> boxes.stream()
                .filter(b -> b.getVersionNumber() == (int) i.getArgument(0)).findFirst());
        // like the database: an unsaved box gets its id, its stop factors theirs
        when(groupRepository.saveAndFlush(any(StopFactorGroup.class))).thenAnswer(i -> {
            StopFactorGroup box = i.getArgument(0);
            if (box.getId() == null) {
                box.setId(nextId++);
                boxes.add(box);
            }
            box.getFactors().stream().filter(f -> f.getId() == null).forEach(f -> f.setId(nextId++));
            return box;
        });
        when(factorRepository.findAllCurrent()).thenAnswer(i -> current().toList());
        when(factorRepository.findCurrent(anyLong())).thenAnswer(i -> current()
                .filter(f -> f.getFactorId().equals(i.getArgument(0))).findFirst());
        when(factorRepository.findHistory(anyLong())).thenAnswer(i -> boxes.stream()
                .flatMap(b -> b.getFactors().stream())
                .filter(f -> f.getFactorId().equals(i.getArgument(0))).toList());
        when(factorRepository.findCurrentVersionStarts()).thenReturn(List.of());
        when(factorRepository.nextFactorId()).thenAnswer(i -> nextFactorId++);
        when(factorRepository.countChecks(anyLong())).thenAnswer(i -> checkCounts);
        when(factorRepository.findProductsByIds(any())).thenReturn(List.of());
    }

    // ================================================================================================
    // Writes: the next box
    // ================================================================================================

    @Test
    @DisplayName("update: the next box with copies of all stop factors; only the changed one gets its next version")
    void updateMakesTheNextBox() {
        StopFactor target = existing("Просрочка 30-60");
        StopFactor other = existing("Другой");
        StopFactorGroup box1 = box(0);

        StopFactorResponse updated = service.update(target.getFactorId(), request("Просрочка 30-90", "Новый порог"));

        StopFactorGroup box2 = box(1);
        assertThat(box1.isCurrent()).isFalse();
        assertThat(box1.getValidTo()).isEqualTo(box2.getValidFrom());
        assertThat(box2.getVersionNumber()).isEqualTo(2);
        assertThat(box2.getChangeReason()).isEqualTo("Новый порог");
        assertThat(box2.getFactors()).extracting(f -> f.getFactorId() + " v" + f.getVersionNumber() + " " + f.getName())
                .containsExactly(target.getFactorId() + " v2 Просрочка 30-90", other.getFactorId() + " v1 Другой");
        assertThat(box2.getFactors()).noneMatch(f -> f == target || f == other);
        // the rows of the closed box stay as they were
        assertThat(target.getName()).isEqualTo("Просрочка 30-60");
        assertThat(updated.versionNumber()).isEqualTo(2);
        assertThat(updated.changeReason()).isEqualTo("Новый порог");
    }

    @Test
    @DisplayName("update: nothing differs, no new box")
    void updateWithoutChanges() {
        StopFactor target = existing("Просрочка 30-60");

        StopFactorResponse same = service.update(target.getFactorId(), request("Просрочка 30-60", "Ничего"));

        assertThat(same.versionId()).isEqualTo(target.getId());
        assertThat(boxes).hasSize(1);
    }

    @Test
    @DisplayName("update: the reason is required")
    void updateNeedsReason() {
        StopFactor target = existing("Просрочка 30-60");

        assertThatThrownBy(() -> service.update(target.getFactorId(), request("Другое", " ")))
                .isInstanceOf(BusinessValidationException.class);
        assertThat(boxes).hasSize(1);
    }

    @Test
    @DisplayName("create: the next box with the new stop factor, version 1, checked last")
    void create() {
        StopFactor other = existing("Просрочка 30-60");

        StopFactorResponse created = service.create(request("Новый", null));

        assertThat(box(1).getFactors()).extracting(StopFactor::getName).containsExactly("Просрочка 30-60", "Новый");
        assertThat(created.versionNumber()).isEqualTo(1);
        assertThat(created.orderNumber()).isEqualTo(other.getOrderNumber() + 1);
        assertThat(created.changeReason()).isEqualTo("Создание стоп-фактора");
    }

    @Test
    @DisplayName("create: an inactive or missing metric is refused, no box is made")
    void createValidation() {
        assertThatThrownBy(() -> service.create(new StopFactorRequest(11L, "Отключённая", null, true, null,
                bucketParams(), List.of(), Set.of(), null)))
                .isInstanceOf(BusinessValidationException.class)
                .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors()).anyMatch(m -> m.contains("отключена")));
        assertThatThrownBy(() -> service.create(new StopFactorRequest(99L, "Нет метрики", null, true, null,
                bucketParams(), List.of(), Set.of(), null)))
                .isInstanceOf(BusinessValidationException.class);
        assertThat(boxes).hasSize(1);
    }

    @Test
    @DisplayName("changeStatus: the next box with it switched off; switching off again changes nothing")
    void changeStatus() {
        StopFactor target = existing("Просрочка 30-60");

        StopFactorResponse off = service.changeStatus(target.getFactorId(), false, "Временно");
        StopFactorResponse again = service.changeStatus(target.getFactorId(), false, "Ещё раз");

        assertThat(off.active()).isFalse();
        assertThat(off.versionNumber()).isEqualTo(2);
        assertThat(again.versionId()).isEqualTo(off.versionId());
        assertThat(boxes).hasSize(2);
    }

    @Test
    @DisplayName("delete: the next box without it; its history tells why it ended")
    void delete() {
        StopFactor target = existing("Просрочка 30-60");
        existing("Другой");

        service.delete(target.getFactorId(), "Больше не нужен");

        assertThat(box(1).getFactors()).extracting(StopFactor::getName).containsExactly("Другой");
        assertThat(service.search(null, null, null, null, null)).extracting(StopFactorResponse::name).containsExactly("Другой");
        assertThat(service.getVersions(target.getFactorId())).singleElement()
                .satisfies(v -> {
                    assertThat(v.current()).isFalse();
                    assertThat(v.endReason()).isEqualTo("Больше не нужен");
                });
        assertThatThrownBy(() -> service.get(target.getFactorId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(target.getFactorId(), "Ещё раз")).isInstanceOf(NotFoundException.class);
    }

    // ================================================================================================
    // History: read from the boxes
    // ================================================================================================

    @Test
    @DisplayName("history: a version lasts across the boxes it was copied into; each with its checks")
    void history() {
        StopFactor target = existing("Просрочка 30-60");
        StopFactor other = existing("Другой");
        service.update(other.getFactorId(), request("Другой 2", "Другой изменён"));     // box 2: target copied, still v1
        service.update(target.getFactorId(), request("Просрочка 30-90", "Новый порог"));  // box 3: target v2
        checkCounts.add(new Object[]{1, "REJECTED", 3L});
        checkCounts.add(new Object[]{1, "APPROVED", 7L});
        checkCounts.add(new Object[]{2, "UNDERWRITER", 2L});

        List<StopFactorResponse> history = service.getVersions(target.getFactorId());

        assertThat(history).extracting(StopFactorResponse::versionNumber).containsExactly(2, 1);
        assertThat(history.get(0).current()).isTrue();
        assertThat(history.get(0).changeReason()).isEqualTo("Новый порог");
        assertThat(history.get(0).checks()).isEqualTo(new StopFactorResponse.Checks(2, 0, 2));
        // v1: from box 1 until box 3 replaced it, through box 2
        assertThat(history.get(1).validFrom()).isEqualTo(box(0).getValidFrom());
        assertThat(history.get(1).validTo()).isEqualTo(box(1).getValidTo());
        assertThat(history.get(1).changeReason()).isEqualTo("Стоп-факторы SPR");
        assertThat(history.get(1).checks()).isEqualTo(new StopFactorResponse.Checks(10, 3, 0));
    }

    @Test
    @DisplayName("search: by segment, status and text")
    void search() {
        existing("Просрочка розница").setSegment(RETAIL);
        existing("Просрочка МСБ").setSegment(MSB);
        existing("Комитет").setActive(false);

        assertThat(names(service.search(null, RETAIL, null, null, null))).containsExactly("Просрочка розница", "Комитет");
        assertThat(names(service.search(null, null, null, false, null))).containsExactly("Комитет");
        assertThat(names(service.search(null, null, null, null, "мсб"))).containsExactly("Просрочка МСБ");
    }

    // ================================================================================================
    // Fixtures
    // ================================================================================================

    private StopFactorGroup box(int index) {
        return boxes.get(index);
    }

    private Stream<StopFactor> current() {
        return boxes.stream().filter(StopFactorGroup::isCurrent).flatMap(b -> b.getFactors().stream())
                .sorted(Comparator.comparingInt(StopFactor::getOrderNumber));
    }

    /** A stop factor of the first box, version 1, checked last. */
    private StopFactor existing(String name) {
        StopFactorGroup box = box(0);
        StopFactor factor = StopFactor.first(box, nextFactorId++, box.getFactors().size() + 1);
        factor.setId(nextId++);
        factor.setMetric(metrics.getFirst());
        factor.setName(name);
        factor.setActive(true);
        factor.setParams(new LinkedHashMap<>(bucketParams()));
        factor.addCondition(REJECTED, GT, "2", null, INTEGER);
        box.add(factor);
        return factor;
    }

    private static StopFactorRequest request(String name, String reason) {
        return new StopFactorRequest(10L, name, null, true, null, bucketParams(),
                List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of(), reason);
    }

    private static Map<String, Object> bucketParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("periodFromMonth", 1);
        params.put("periodToMonth", 6);
        params.put("minOverdueDays", 30);
        params.put("maxOverdueDays", 60);
        return params;
    }

    private static StopFactorMetric metric(long id, String title, boolean active) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setId(id);
        metric.setTitle(title);
        metric.setCalculator(CalculatorType.OVERDUE_BUCKET_COUNT);
        metric.setParams(List.of(
                new MetricParam("periodFromMonth", "Период с (месяц)", true),
                new MetricParam("periodToMonth", "Период по (месяц)", true),
                new MetricParam("minOverdueDays", "Просрочка от (дней)", true),
                new MetricParam("maxOverdueDays", "Просрочка до (дней)", false)));
        metric.setActive(active);
        return metric;
    }

    private static List<String> names(List<StopFactorResponse> factors) {
        return factors.stream().map(StopFactorResponse::name).toList();
    }
}
