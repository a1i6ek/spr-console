package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.KibRefusalStatusesCalculator;
import kg.bakai.stopfactor.calculator.OverdueBucketCountCalculator;
import kg.bakai.stopfactor.calculator.RestructuringCountCalculator;
import kg.bakai.stopfactor.config.CalculatorConfig;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.dto.MetricParam;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;
import static kg.bakai.stopfactor.enums.ConditionOperator.GT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the console service, no database: group versions (snapshots), factor writes and the factor
 * endpoints. Groups and factors are real entities in memory, repositories are mocks, the validator is real with
 * the same calculators as the application.
 */
class StopFactorConsoleServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final StopFactorGroupRepository groupRepository = mock(StopFactorGroupRepository.class);
    private final StopFactorMetricRepository metricRepository = mock(StopFactorMetricRepository.class);
    private final StopFactorRepository factorRepository = mock(StopFactorRepository.class);

    /** Every group version, like the table: closed ones stay for the checks made with them. */
    private final List<StopFactorGroup> versions = new ArrayList<>();
    private final List<StopFactorMetric> metrics = new ArrayList<>();
    private long nextId = 1;

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
        service = new StopFactorConsoleServiceImpl(groupRepository, metricRepository, factorRepository,
                new StopFactorValidation(registry, groupRepository));

        metrics.add(metric(10, "Просроченная задолженность", true));
        metrics.add(metric(11, "Просрочка 90 плюс", true));
        metrics.add(metric(12, "Отключённая метрика", false));

        when(groupRepository.findAllCurrent()).thenAnswer(invocation -> versions.stream()
                .filter(StopFactorGroup::isCurrent)
                .sorted(Comparator.comparingInt(StopFactorGroup::getOrderNumber))
                .toList());
        when(groupRepository.findMaxCurrentOrderNumber()).thenAnswer(invocation -> versions.stream()
                .filter(StopFactorGroup::isCurrent)
                .mapToInt(StopFactorGroup::getOrderNumber)
                .max().orElse(0));
        when(groupRepository.findCurrent(anyLong())).thenAnswer(invocation -> versions.stream()
                .filter(StopFactorGroup::isCurrent)
                .filter(g -> g.getGroupId().equals(invocation.getArgument(0)))
                .findFirst());
        when(groupRepository.findVersions(anyLong())).thenAnswer(invocation -> versions.stream()
                .filter(g -> g.getGroupId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparingInt(StopFactorGroup::getVersionNumber).reversed())
                .toList());
        // the lock query takes the first version of a group (root_id IS NULL), like SELECT ... FOR UPDATE
        when(groupRepository.lockGroup(anyLong())).thenAnswer(invocation -> versions.stream()
                .filter(g -> g.getRootId() == null && g.getGroupId().equals(invocation.getArgument(0)))
                .findFirst());
        // like the database: an unsaved version gets its id, a saved one keeps it
        when(groupRepository.saveAndFlush(any(StopFactorGroup.class))).thenAnswer(invocation -> {
            StopFactorGroup group = invocation.getArgument(0);
            if (group.getId() == null) {
                group.setId(nextId++);
                versions.add(group);
            }
            return group;
        });
        when(metricRepository.findAllById(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return metrics.stream().filter(m -> ids.contains(m.getId())).toList();
        });
        when(factorRepository.findById(anyLong())).thenAnswer(invocation -> versions.stream()
                .flatMap(g -> g.getFactors().stream())
                .filter(f -> Objects.equals(f.getId(), invocation.getArgument(0)))
                .findFirst());
        when(groupRepository.findProductsByIds(any())).thenReturn(List.of());
    }

    // ================================================================================================
    // Fixtures
    // ================================================================================================

    private StopFactorMetric metric(long id, String title, boolean active) {
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

    /** A current group version with one factor, like a row of stop_factors.stop_factor_group. */
    private StopFactorGroup group(String name, int order, StopFactorMetric metric, String factorName) {
        StopFactorGroup group = new StopFactorGroup();
        group.setId(nextId++);
        group.setName(name);
        group.setOrderNumber(order);
        group.setValidFrom(NOW);
        factor(group, metric, factorName);
        versions.add(group);
        return group;
    }

    private StopFactor factor(StopFactorGroup group, StopFactorMetric metric, String name) {
        StopFactor factor = group.addFactor();
        factor.setId(nextId++);
        factor.setMetric(metric);
        factor.setName(name);
        factor.setParams(bucketParams());
        factor.addCondition(REJECTED, GT, "2", null, INTEGER);
        return factor;
    }

    private static Map<String, Object> bucketParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("periodFromMonth", 1);
        params.put("periodToMonth", 6);
        params.put("minOverdueDays", 30);
        params.put("maxOverdueDays", 60);
        return params;
    }

    private static StopFactorDto factorDto(StopFactorMetric metric, String name) {
        return new StopFactorDto(null, metric.getId(), null, name, null, null, null, bucketParams(),
                List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of(), null);
    }

    private static StopFactorGroupRequest groupRequest(String name, List<StopFactorDto> factors, String reason) {
        return new StopFactorGroupRequest(name, null, null, null, factors, reason);
    }

    /** The versions passed to saveAndFlush, in call order: the closed current one first, the new one second. */
    private List<StopFactorGroup> savedVersions() {
        ArgumentCaptor<StopFactorGroup> captor = ArgumentCaptor.forClass(StopFactorGroup.class);
        verify(groupRepository, times(2)).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    // ================================================================================================
    // Group snapshots: every change closes the version and keeps it for the checks made with it
    // ================================================================================================

    @Nested
    @DisplayName("Group snapshots: versions are created, closed and kept")
    class GroupSnapshots {

        @Test
        @DisplayName("create: the first version, current, after the other groups, with the change reason")
        void createFirstVersion() {
            group("Первая", 1, metrics.get(0), "фактор 1");
            StopFactorGroupDto created = service.create(
                    groupRequest("Комитет", List.of(factorDto(metrics.get(0), "Просрочка 30-60")), null));

            assertThat(created.versionNumber()).isEqualTo(1);
            assertThat(created.current()).isTrue();
            assertThat(created.orderNumber()).isEqualTo(2);
            assertThat(created.changeReason()).isEqualTo("Создание группы");
            assertThat(created.factors()).hasSize(1);
            assertThat(created.factors().get(0).name()).isEqualTo("Просрочка 30-60");
            assertThat(created.factors().get(0).params())
                    .containsEntry("periodFromMonth", 1).containsEntry("maxOverdueDays", 60);
            assertThat(created.factors().get(0).summary())
                    .startsWith("Просроченная задолженность")
                    .contains("Период по (месяц) 6", "60");
        }

        @Test
        @DisplayName("create: an explicit display order is kept")
        void createWithOrder() {
            StopFactorGroupDto created = service.create(new StopFactorGroupRequest("Комитет", null, 5, null,
                    List.of(factorDto(metrics.get(0), "Фактор")), null));

            assertThat(created.orderNumber()).isEqualTo(5);
        }

        @Test
        @DisplayName("update: closes the current version and opens the next one with copies of every factor")
        void updateCreatesNewVersion() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");
            long oldVersionId = current.getId();
            StopFactorDto changed = factorDto(metrics.get(0), "Просрочка 30-90");

            StopFactorGroupDto updated = service.update(current.getGroupId(),
                    groupRequest("Комитет (новый)", List.of(changed), "Новые лимиты"));

            assertThat(updated.groupId()).isEqualTo(current.getGroupId());
            assertThat(updated.versionId()).isNotEqualTo(oldVersionId);
            assertThat(updated.versionNumber()).isEqualTo(2);
            assertThat(updated.current()).isTrue();
            assertThat(updated.name()).isEqualTo("Комитет (новый)");
            assertThat(updated.changeReason()).isEqualTo("Новые лимиты");

            List<StopFactorGroup> saved = savedVersions();
            StopFactorGroup closed = saved.get(0);
            StopFactorGroup next = saved.get(1);
            assertThat(closed.getId()).isEqualTo(oldVersionId);
            assertThat(closed.isCurrent()).isFalse();
            assertThat(closed.getValidTo()).isNotNull();
            assertThat(next.getRootId()).isEqualTo(oldVersionId);
            assertThat(next.getVersionNumber()).isEqualTo(2);
            assertThat(next.getChangeReason()).isEqualTo("Новые лимиты");
            assertThat(next.getFactors()).hasSize(1);
            assertThat(next.getFactors().get(0)).isNotSameAs(current.getFactors().get(0));
            assertThat(next.getFactors().get(0).getName()).isEqualTo("Просрочка 30-90");
            assertThat(next.getFactors().get(0).getConditions()).hasSize(1);
        }

        @Test
        @DisplayName("update: nothing differs - no new version")
        void updateUnchangedReturnsCurrent() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");

            StopFactorGroupDto result = service.update(current.getGroupId(),
                    groupRequest("Комитет", List.of(factorDto(metrics.get(0), "Просрочка 30-60")), "без изменений"));

            assertThat(result.versionId()).isEqualTo(current.getId());
            assertThat(result.current()).isTrue();
            verify(groupRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("update: a stop factor kept as it is may keep an inactive metric")
        void updateKeepsFactorWithInactiveMetric() {
            // the factor references metric 12; metric 12 is deactivated afterwards
            StopFactorGroup current = group("Комитет", 1, metrics.get(2), "Просрочка 30-60");

            StopFactorGroupDto result = service.update(current.getGroupId(),
                    groupRequest("Комитет (новый)", List.of(factorDto(metrics.get(2), "Просрочка 30-60")), "переименование"));

            assertThat(result.versionNumber()).isEqualTo(2);
            assertThat(result.factors().get(0).metricId()).isEqualTo(12);
        }

        @Test
        @DisplayName("changeStatus: off - a new closed-current / current pair, off in the next version")
        void changeStatusOff() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");

            StopFactorGroupDto off = service.changeStatus(current.getGroupId(), false, "Временно отключена");

            assertThat(off.active()).isFalse();
            assertThat(off.versionNumber()).isEqualTo(2);
            List<StopFactorGroup> saved = savedVersions();
            assertThat(saved.get(0).isCurrent()).isFalse();
            assertThat(saved.get(1).isActive()).isFalse();
            assertThat(saved.get(1).getFactors()).hasSize(1);
        }

        @Test
        @DisplayName("changeStatus: the same state - no new version")
        void changeStatusSameReturnsCurrent() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");

            StopFactorGroupDto result = service.changeStatus(current.getGroupId(), true, "уже включена");

            assertThat(result.versionId()).isEqualTo(current.getId());
            verify(groupRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("delete: the group leaves the console; a last inactive version records the reason")
        void deleteClosesWithLastVersion() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");

            service.delete(current.getGroupId(), "Больше не используется");

            List<StopFactorGroup> saved = savedVersions();
            StopFactorGroup last = saved.get(1);
            assertThat(last.getVersionNumber()).isEqualTo(2);
            assertThat(last.isActive()).isFalse();
            assertThat(last.getValidTo()).isEqualTo(last.getValidFrom());
            assertThat(last.getChangeReason()).isEqualTo("Больше не используется");
            // no current version stays: nothing reads the group anymore
            assertThat(service.getAll(null, null, null, null)).isEmpty();
        }

        @Test
        @DisplayName("getVersions: newest first, the closed version kept")
        void versionsNewestFirst() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");
            service.changeStatus(current.getGroupId(), false, "Временно отключена");

            List<StopFactorGroupDto> all = service.getVersions(current.getGroupId());

            assertThat(all).hasSize(2);
            assertThat(all.get(0).versionNumber()).isEqualTo(2);
            assertThat(all.get(1).versionNumber()).isEqualTo(1);
            assertThat(all.get(1).current()).isFalse();
        }

        @Test
        @DisplayName("getVersions: an unknown group - not found")
        void versionsNotFound() {
            assertThatThrownBy(() -> service.getVersions(999))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("не найдена");
        }

        @Test
        @DisplayName("get: an unknown group - not found")
        void getNotFound() {
            assertThatThrownBy(() -> service.get(999))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("не найдена");
        }
    }

    // ================================================================================================
    // Reorder: the display order changes in place, without new versions
    // ================================================================================================

    @Nested
    @DisplayName("Reorder: every group id, exactly once, in the new order")
    class Reorder {

        @Test
        @DisplayName("reorder: numbers are reassigned, no new versions")
        void reorderReassignsNumbers() {
            StopFactorGroup first = group("Первая", 1, metrics.get(0), "фактор 1");
            StopFactorGroup second = group("Вторая", 2, metrics.get(1), "фактор 2");
            StopFactorGroup third = group("Третья", 3, metrics.get(0), "фактор 3");

            List<StopFactorGroupDto> reordered = service.reorder(
                    List.of(second.getGroupId(), third.getGroupId(), first.getGroupId()));

            assertThat(reordered).extracting(StopFactorGroupDto::name)
                    .containsExactly("Вторая", "Третья", "Первая");
            assertThat(second.getOrderNumber()).isEqualTo(1);
            assertThat(third.getOrderNumber()).isEqualTo(2);
            assertThat(first.getOrderNumber()).isEqualTo(3);
            verify(groupRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("reorder: an unknown id is refused")
        void reorderUnknownGroup() {
            group("Первая", 1, metrics.get(0), "фактор 1");
            assertThatThrownBy(() -> service.reorder(List.of(999L)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("не найдена")));
        }

        @Test
        @DisplayName("reorder: a duplicate and a missing id are refused")
        void reorderDuplicateAndMissing() {
            StopFactorGroup first = group("Первая", 1, metrics.get(0), "фактор 1");
            StopFactorGroup second = group("Вторая", 2, metrics.get(1), "фактор 2");

            assertThatThrownBy(() -> service.reorder(List.of(first.getGroupId(), first.getGroupId())))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("несколько раз")));

            assertThatThrownBy(() -> service.reorder(List.of(first.getGroupId())))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("В списке нет группы №" + second.getGroupId())));
        }
    }

    // ================================================================================================
    // Validation
    // ================================================================================================

    @Nested
    @DisplayName("Validation: names, metrics and the change reason")
    class Validation {

        @Test
        @DisplayName("create: a group with the same name is refused")
        void duplicateName() {
            group("Комитет", 1, metrics.get(0), "фактор 1");

            assertThatThrownBy(() -> service.create(
                    groupRequest("Комитет", List.of(factorDto(metrics.get(1), "Другой фактор")), null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("уже есть")));
        }

        @Test
        @DisplayName("create: an inactive metric is refused for a new stop factor")
        void inactiveMetric() {
            assertThatThrownBy(() -> service.create(
                    groupRequest("Комитет", List.of(factorDto(metrics.get(2), "На отключённой метрике")), null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("отключена")));
        }

        @Test
        @DisplayName("create: a group needs at least one stop factor")
        void noFactors() {
            assertThatThrownBy(() -> service.create(groupRequest("Пустая", List.of(), null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("хотя бы один стоп-фактор")));
        }

        @Test
        @DisplayName("update without a change reason is refused")
        void reasonRequired() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");

            assertThatThrownBy(() -> service.update(current.getGroupId(),
                    groupRequest("Комитет (новый)", List.of(factorDto(metrics.get(0), "Фактор")), "  ")))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("причину изменения")));
        }

        @Test
        @DisplayName("an unknown metric is refused")
        void unknownMetric() {
            assertThatThrownBy(() -> service.create(new StopFactorGroupRequest("Другой комитет", null, null, null,
                    List.of(new StopFactorDto(null, 999L, null, "Фактор", null, null, null, bucketParams(),
                            List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of(), null)), null)))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("метрика №999 не найдена")));
        }
    }

    // ================================================================================================
    // Stop factors through the group endpoints
    // ================================================================================================

    @Nested
    @DisplayName("Stop factors of a group: add, replace, remove - each a new version")
    class GroupFactorWrites {

        @Test
        @DisplayName("addFactor: appended after the others, a new version")
        void addFactorAppends() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Фактор 1");

            StopFactorGroupDto updated = service.addFactor(current.getGroupId(),
                    factorDto(metrics.get(1), "Фактор 2"), "Добавлен фактор");

            assertThat(updated.versionNumber()).isEqualTo(2);
            assertThat(updated.factors()).extracting(StopFactorDto::name)
                    .containsExactly("Фактор 1", "Фактор 2");
        }

        @Test
        @DisplayName("updateFactor: replaced in place, a new version")
        void updateFactorReplaces() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Фактор 1");
            StopFactor second = factor(current, metrics.get(1), "Фактор 2");

            StopFactorGroupDto updated = service.updateFactor(current.getGroupId(), second.getId(),
                    factorDto(metrics.get(1), "Фактор 2 (новый)"), "Новый лимит");

            assertThat(updated.factors()).extracting(StopFactorDto::name)
                    .containsExactly("Фактор 1", "Фактор 2 (новый)");
        }

        @Test
        @DisplayName("deleteFactor: removed, a new version")
        void deleteFactorRemoves() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Фактор 1");
            StopFactor second = factor(current, metrics.get(1), "Фактор 2");

            StopFactorGroupDto updated = service.deleteFactor(current.getGroupId(), second.getId(), "Больше не нужен");

            assertThat(updated.versionNumber()).isEqualTo(2);
            assertThat(updated.factors()).extracting(StopFactorDto::name).containsExactly("Фактор 1");
        }

        @Test
        @DisplayName("deleteFactor: the only factor of a group stays")
        void deleteFactorLastOneStays() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Единственный фактор");

            assertThatThrownBy(() -> service.deleteFactor(current.getGroupId(),
                    current.getFactors().get(0).getId(), "попытка удалить"))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("единственный стоп-фактор")));
        }

        @Test
        @DisplayName("updateFactor: a factor of a closed version is not there")
        void updateFactorOfOldVersionNotFound() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Фактор 1");
            long oldFactorId = current.getFactors().get(0).getId();
            service.changeStatus(current.getGroupId(), false, "Временно отключена");

            assertThatThrownBy(() -> service.updateFactor(current.getGroupId(), oldFactorId,
                    factorDto(metrics.get(0), "Фактор 1 (новый)"), "попытка"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("не найден в текущей версии");
        }

        @Test
        @DisplayName("rename without a params change: the summary still describes the rule itself")
        void renameKeepsSummaryTrueToParams() {
            StopFactorGroup current = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");

            StopFactorGroupDto updated = service.updateFactor(current.getGroupId(), current.getFactors().get(0).getId(),
                    factorDto(metrics.get(0), "Просрочка 30-90"), "переименование");

            StopFactorDto renamed = updated.factors().get(0);
            assertThat(renamed.name()).isEqualTo("Просрочка 30-90");
            // the label now says 30-90, the rule is still 30-60: the summary follows the params, not the name
            assertThat(renamed.summary())
                    .startsWith("Просроченная задолженность")
                    .contains("60")
                    .doesNotContain("90");
        }
    }

    // ================================================================================================
    // The factor endpoints: search and get across the groups
    // ================================================================================================

    @Nested
    @DisplayName("Factor search and get across the groups")
    class FactorReads {

        private StopFactorGroup committee;
        private StopFactorGroup antifraud;
        private StopFactor overdue;
        private StopFactor restructuring;
        private StopFactor msbOverdue;

        @BeforeEach
        void buildGroups() {
            committee = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");
            overdue = committee.getFactors().get(0);
            overdue.setSegment(RETAIL);
            restructuring = factor(committee, metrics.get(1), "Реструктуризации");
            restructuring.setSegment(null);
            restructuring.setActive(false);
            restructuring.setDescription("внутренние проверки");

            antifraud = group("Антифрод", 2, metrics.get(0), "Просрочка МСБ");
            msbOverdue = antifraud.getFactors().get(0);
            msbOverdue.setSegment(MSB);
        }

        @Test
        @DisplayName("no filters: every factor of every current group, in display order then factor order")
        void allFactorsInOrder() {
            List<StopFactorResponse> found = service.searchFactors(null, null, null, null, null, null);

            assertThat(found).extracting(f -> f.id())
                    .containsExactly(overdue.getId(), restructuring.getId(), msbOverdue.getId());
            assertThat(found.get(0).group().name()).isEqualTo("Комитет");
            assertThat(found.get(0).metric().title()).isEqualTo("Просроченная задолженность");
            assertThat(found.get(0).conditions()).hasSize(1);
            assertThat(found.get(2).group().orderNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("filter by group, metric, segment, active and search")
        void filters() {
            assertThat(service.searchFactors(committee.getGroupId(), null, null, null, null, null))
                    .extracting(f -> f.id()).containsExactly(overdue.getId(), restructuring.getId());
            assertThat(service.searchFactors(null, 11L, null, null, null, null))
                    .extracting(f -> f.id()).containsExactly(restructuring.getId());
            // a factor without a segment checks applications of every segment
            assertThat(service.searchFactors(null, null, RETAIL, null, null, null))
                    .extracting(f -> f.id()).containsExactly(overdue.getId(), restructuring.getId());
            assertThat(service.searchFactors(null, null, MSB, null, null, null))
                    .extracting(f -> f.id()).containsExactly(restructuring.getId(), msbOverdue.getId());
            assertThat(service.searchFactors(null, null, null, null, false, null))
                    .extracting(f -> f.id()).containsExactly(restructuring.getId());
            assertThat(service.searchFactors(null, null, null, null, null, "реструкт"))
                    .extracting(f -> f.id()).containsExactly(restructuring.getId());
            // search covers the description too
            assertThat(service.searchFactors(null, null, null, null, null, "ПРОВЕРКИ"))
                    .extracting(f -> f.id()).containsExactly(restructuring.getId());
        }

        @Test
        @DisplayName("filter by product: factors of its segment and of every segment")
        void filterByProduct() {
            List<StopFactorResponse> found = service.searchFactors(null, null, null, 5L, null, null);

            // the product does not exist (no projection stubbed) - only factors without a product limit
            // and without a segment match
            assertThat(found).extracting(f -> f.id()).containsExactly(restructuring.getId());
        }

        @Test
        @DisplayName("search is case-insensitive and matches a part of the name")
        void searchCaseInsensitive() {
            assertThat(service.searchFactors(null, null, null, null, null, "ПРОСРОЧКА"))
                    .extracting(f -> f.id()).containsExactly(overdue.getId(), msbOverdue.getId());
        }

        @Test
        @DisplayName("getFactor: the factor with its group version and metric")
        void getFactor() {
            StopFactorResponse found = service.getFactor(overdue.getId());

            assertThat(found.id()).isEqualTo(overdue.getId());
            assertThat(found.name()).isEqualTo("Просрочка 30-60");
            assertThat(found.segment()).isEqualTo(RETAIL);
            assertThat(found.metric().id()).isEqualTo(10);
            assertThat(found.group().groupId()).isEqualTo(committee.getGroupId());
            assertThat(found.group().versionNumber()).isEqualTo(1);
            assertThat(found.group().groupActive()).isTrue();
        }

        @Test
        @DisplayName("getFactor: a factor of a closed version still reads")
        void getFactorOfClosedVersion() {
            long factorId = overdue.getId();
            service.changeStatus(committee.getGroupId(), false, "Временно отключена");

            StopFactorResponse found = service.getFactor(factorId);

            assertThat(found.id()).isEqualTo(factorId);
            assertThat(found.group().versionNumber()).isEqualTo(1);
            assertThat(found.group().groupActive()).isTrue(); // the rule it was saved with
        }

        @Test
        @DisplayName("getFactor: an unknown factor - not found")
        void getFactorNotFound() {
            assertThatThrownBy(() -> service.getFactor(999))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("не найден");
        }
    }

    // ================================================================================================
    // The factor endpoints: writes
    // ================================================================================================

    @Nested
    @DisplayName("Factor writes found by id: each a new version of the group")
    class FactorWrites {

        private StopFactorGroup committee;
        private StopFactor overdue;
        private StopFactor restructuring;

        @BeforeEach
        void buildGroup() {
            committee = group("Комитет", 1, metrics.get(0), "Просрочка 30-60");
            overdue = committee.getFactors().get(0);
            restructuring = factor(committee, metrics.get(1), "Реструктуризации");
        }

        @Test
        @DisplayName("updateFactor: replaced in place, the response is the new factor of the new version")
        void updateFactorById() {
            StopFactorRequest request = new StopFactorRequest(10L, "Просрочка 30-90", null, null, RETAIL,
                    bucketParams(), List.of(new ConditionDto(REJECTED, GT, "3", null, INTEGER)), Set.of());

            StopFactorResponse updated = service.updateFactor(restructuring.getId(), request, "Новый лимит");

            assertThat(updated.name()).isEqualTo("Просрочка 30-90");
            assertThat(updated.segment()).isEqualTo(RETAIL);
            assertThat(updated.group().groupId()).isEqualTo(committee.getGroupId());
            assertThat(updated.group().versionNumber()).isEqualTo(2);
            assertThat(updated.metric().id()).isEqualTo(10);
            assertThat(updated.summary()).contains("Просроченная задолженность");

            List<StopFactorGroup> saved = savedVersions();
            StopFactorGroup next = saved.get(1);
            // the replacement stays at the factor's place, the other factor is copied untouched
            assertThat(next.getFactors()).extracting(StopFactor::getName)
                    .containsExactly("Просрочка 30-60", "Просрочка 30-90");
            assertThat(next.getFactors().get(0).getConditions()).hasSize(1);
        }

        @Test
        @DisplayName("updateFactor: a missing change reason is refused")
        void updateFactorReasonRequired() {
            StopFactorRequest request = new StopFactorRequest(10L, "Новое имя", null, null, null,
                    bucketParams(), List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of());

            assertThatThrownBy(() -> service.updateFactor(overdue.getId(), request, null))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("причину изменения")));
        }

        @Test
        @DisplayName("updateFactor: an inactive metric is refused for a changed stop factor")
        void updateFactorInactiveMetric() {
            StopFactorRequest request = new StopFactorRequest(12L, "Новое имя", null, null, null,
                    bucketParams(), List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of());

            assertThatThrownBy(() -> service.updateFactor(overdue.getId(), request, "попытка"))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("отключена")));
        }

        @Test
        @DisplayName("updateFactor: an unknown factor - not found")
        void updateFactorNotFound() {
            StopFactorRequest request = new StopFactorRequest(10L, "Новое имя", null, null, null,
                    bucketParams(), List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of());

            assertThatThrownBy(() -> service.updateFactor(999, request, "попытка"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("не найден");
        }

        @Test
        @DisplayName("updateFactor: a factor of a closed version cannot change")
        void updateFactorOfClosedVersion() {
            long factorId = overdue.getId();
            service.changeStatus(committee.getGroupId(), false, "Временно отключена");
            StopFactorRequest request = new StopFactorRequest(10L, "Новое имя", null, null, null,
                    bucketParams(), List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of());

            assertThatThrownBy(() -> service.updateFactor(factorId, request, "попытка"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("закрытой версии");
        }

        @Test
        @DisplayName("changeFactorStatus: off - a new version, the factor off, the others untouched")
        void changeFactorStatusOff() {
            StopFactorResponse off = service.changeFactorStatus(restructuring.getId(), false, "Временно отключен");

            assertThat(off.active()).isFalse();
            assertThat(off.name()).isEqualTo("Реструктуризации");
            assertThat(off.group().versionNumber()).isEqualTo(2);

            List<StopFactorGroup> saved = savedVersions();
            StopFactorGroup next = saved.get(1);
            assertThat(next.getFactors().get(0).isActive()).isTrue();
            assertThat(next.getFactors().get(1).isActive()).isFalse();
        }

        @Test
        @DisplayName("changeFactorStatus: the same state - no new version")
        void changeFactorStatusSameReturnsCurrent() {
            StopFactorResponse result = service.changeFactorStatus(restructuring.getId(), true, "уже включен");

            assertThat(result.active()).isTrue();
            assertThat(result.group().versionNumber()).isEqualTo(1);
            verify(groupRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("changeFactorStatus: a missing change reason is refused")
        void changeFactorStatusReasonRequired() {
            assertThatThrownBy(() -> service.changeFactorStatus(restructuring.getId(), false, " "))
                    .isInstanceOf(BusinessValidationException.class)
                    .satisfies(e -> assertThat(((BusinessValidationException) e).getErrors())
                            .anyMatch(error -> error.contains("причину изменения")));
        }
    }

    // ================================================================================================
    // getAll: the group list with its filters
    // ================================================================================================

    @Nested
    @DisplayName("Group list filters")
    class GroupList {

        @Test
        @DisplayName("getAll: active, metric and segment filters")
        void filters() {
            StopFactorGroup active = group("Активная", 1, metrics.get(0), "Фактор 1");
            StopFactorGroup inactive = group("Отключённая", 2, metrics.get(1), "Фактор 2");
            inactive.setActive(false);
            active.getFactors().get(0).setSegment(RETAIL);
            inactive.getFactors().get(0).setSegment(MSB);

            assertThat(service.getAll(null, null, null, null)).extracting(StopFactorGroupDto::name)
                    .containsExactly("Активная", "Отключённая");
            assertThat(service.getAll(true, null, null, null)).extracting(StopFactorGroupDto::name)
                    .containsExactly("Активная");
            assertThat(service.getAll(null, null, RETAIL, null)).extracting(StopFactorGroupDto::name)
                    .containsExactly("Активная");
            assertThat(service.getAll(null, null, MSB, null)).extracting(StopFactorGroupDto::name)
                    .containsExactly("Отключённая");
            assertThat(service.getAll(null, null, null, 11L)).extracting(StopFactorGroupDto::name)
                    .containsExactly("Отключённая");
        }
    }
}
