package kg.bakai.stopfactor;

import kg.bakai.common.enums.ProductSegment;
import jakarta.persistence.EntityManager;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.stopfactor.service.StopFactorService;
import kg.bakai.stopfactor.service.MetricConsoleService;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.entity.MetricParamSchema;
import kg.bakai.stopfactor.enums.ConditionOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kg.bakai.stopfactor.enums.CalculatorType.OVERDUE_BUCKET_COUNT;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stop factors grouped (changelogs 1.3 / 1.4): stop_factor_group is the versioned unit, stop_factor the stop factor of a
 * group version with its param values (params JSON). Every test is rolled back.
 */
@SpringBootTest
@Transactional
class StopFactorRulesTest {

    private static final long OVERDUE_METRIC = 10;
    private static final long RESTRUCTURING_METRIC = 4;
    private static final long ACTIVE_DISPUTES_METRIC = 2;
    private static final long UNJUSTIFIED_DISPUTES_METRIC = 3;

    @Autowired
    private MetricConsoleService metricConsoleService;
    @Autowired
    private StopFactorConsoleService stopFactorConsoleService;
    @Autowired
    private StopFactorService stopFactorService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    // ------------------------------------------------------------------------------------------------
    // Changelogs 1.3 / 1.4
    // ------------------------------------------------------------------------------------------------

    @Test
    void metricParamsAreJsonInTheirOrder() {
        assertThat(metricConsoleService.get(OVERDUE_METRIC).params())
                .extracting(p -> p.key() + " " + p.required())
                .containsExactly("periodFromMonth true", "periodToMonth true", "minOverdueDays true", "maxOverdueDays false");
        assertThat(metricConsoleService.get(RESTRUCTURING_METRIC).params()).extracting(MetricParamSchema::key)
                .containsExactly("source", "periodFromMonth", "periodToMonth");
    }

    @Test
    void everyLegacyStopFactorIsAStopFactorWithItsValues() {
        // a stop factor of the legacy groups has the id of its legacy stop factor:
        // #4 KIB restructurings of months 1..12, #29 months 1..3 with 31+ days, #2 no params
        assertThat(values(4)).isEqualTo(Map.of("source", "KIB", "periodFromMonth", "1", "periodToMonth", "12"));
        assertThat(values(29)).isEqualTo(Map.of("periodFromMonth", "1", "periodToMonth", "3", "minOverdueDays", "31"));
        assertThat(values(2)).isEmpty();
        // the legacy stop factors that kept their own group have one stop factor named like the group
        for (long groupId : List.of(1L, 2L, 3L, 8L, 9L, 10L, 11L, 12L)) {
            StopFactorResponse legacy = stopFactorConsoleService.get(groupId);
            assertThat(legacy.factors()).as("group " + groupId).singleElement()
                    .satisfies(factor -> assertThat(factor.name()).isEqualTo(legacy.name()));
        }
    }

    @Test
    void legacyStopFactorsOfOneMetricAreOneGroupWithStopFactors() {
        StopFactorResponse restructurings = stopFactorConsoleService.get(4);
        assertThat(restructurings.name()).isEqualTo("Реструктуризации");
        assertThat(restructurings.factors()).extracting(factor -> factor.params().get("source") + " " + factor.params().get("periodFromMonth"))
                .containsExactly("KIB 1", "OB 1", "KIB 13", "OB 13");

        // the overdue history: version 1 with the legacy stop factors, version 2 with the 2026 rules
        List<StopFactorResponse> overdue = stopFactorConsoleService.getVersions(13);
        assertThat(overdue).extracting(StopFactorResponse::versionNumber).containsExactly(2, 1);
        assertThat(overdue).allSatisfy(version -> assertThat(version.name()).isEqualTo("Непрерывная просроченная задолженность"));
        assertThat(overdue.getFirst().active()).isTrue();
        assertThat(overdue.getFirst().factors()).hasSize(27);
        assertThat(overdue.get(1).factors()).hasSize(22);
        assertThat(overdue.get(1).factors()).filteredOn(StopFactorDto::active).hasSize(17);
        assertThat(overdue.get(1).factors().getFirst().name()).endsWith("с 1 го по 3 месяц, где максимальная просрочка >= 1 и <= 15");

        // there are no separate groups #5..#7, #14..#34 any more
        assertThat(count("""
                SELECT count(*) FROM stop_factors.stop_factor_group
                WHERE COALESCE(root_id, id) BETWEEN 5 AND 7 OR COALESCE(root_id, id) BETWEEN 14 AND 34""")).isZero();
    }

    @Test
    void anInactiveStopFactorIsKeptButNotChecked() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(
                factor(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 30)),
                new StopFactorDto(null, OVERDUE_METRIC, null, null, null, Map.of("periodFromMonth", 4, "periodToMonth", 6, "minOverdueDays", 30),
                        List.of(new ConditionDto(null, REJECTED, INTEGER, "0")), false, null, null)), null));

        assertThat(created.factors()).extracting(StopFactorDto::active).containsExactly(true, false);
    }

    @Test
    void the2026OverdueHistoryIsOneGroupWithTheStopFactorsOfEveryProduct() {
        StopFactorResponse overdue = stopFactorConsoleService.get(overdueGroup("RETAIL"));

        assertThat(overdue.groupId()).isEqualTo(overdueGroup("MSB")).isEqualTo(13);
        assertThat(overdue.name()).isEqualTo("Непрерывная просроченная задолженность");
        // Розница и ИФЦ 14, every product (months 25..36) 3, МСБ 10
        assertThat(overdue.factors()).extracting(StopFactorDto::segment).containsExactlyElementsOf(java.util.stream.Stream.of(
                Collections.nCopies(14, ProductSegment.RETAIL), Collections.<ProductSegment>nCopies(3, null),
                Collections.nCopies(10, ProductSegment.MSB)).flatMap(List::stream).toList());
        // stop factor 8: months 7..12, 6..10 days - "до 5 - Ок, 6-8 - жёлтая зона, 9+ - отказ"
        StopFactorDto factor = overdue.factors().get(7);
        assertThat(factor.params()).isEqualTo(Map.of("periodFromMonth", 7, "periodToMonth", 12, "minOverdueDays", 6, "maxOverdueDays", 10));
        assertThat(factor.name()).endsWith("с 7 го по 12 месяц, где максимальная просрочка >= 6 и <= 10");
        assertThat(factor.productIds()).hasSize(6);
        assertThat(factor.conditions()).containsExactlyInAnyOrder(
                new ConditionDto(factor.conditions().get(0).id(), REJECTED, INTEGER, "8", ConditionOperator.GT, null),
                new ConditionDto(factor.conditions().get(1).id(), UNDERWRITER, INTEGER, "5", ConditionOperator.RANGE, "8"));
    }

    @Test
    void everyValueIsAParamOfTheMetricOfItsStopFactor() {
        // every key of the params of a stop factor is a param of its metric
        assertThat(count("""
                SELECT count(*) FROM stop_factors.stop_factor f
                CROSS JOIN LATERAL jsonb_object_keys(f.params) AS k(key)
                JOIN stop_factors.stop_factor_metric m ON m.id = f.metric_id
                WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(m.params) AS d(definition)
                                  WHERE d.definition ->> 'key' = k.key)""")).isZero();
        // every required param of the metric has a value in every stop factor
        assertThat(count("""
                SELECT count(*) FROM stop_factors.stop_factor f
                JOIN stop_factors.stop_factor_metric m ON m.id = f.metric_id
                CROSS JOIN LATERAL jsonb_array_elements(m.params) AS d(definition)
                WHERE (d.definition ->> 'required')::boolean AND NOT jsonb_exists(f.params, d.definition ->> 'key')""")).isZero();
        // param definitions on the metric, values on the stop factors
        assertThat(jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'stop_factors' AND column_name = 'params'""", String.class))
                .containsExactlyInAnyOrder("stop_factor_metric", "stop_factor");
    }

    // ------------------------------------------------------------------------------------------------
    // Console
    // ------------------------------------------------------------------------------------------------

    @Test
    void aGroupIsSavedWithItsParamRows() {
        StopFactorResponse created = createOverdueGroup(Map.of("periodFromMonth", 1, "periodToMonth", 6, "minOverdueDays", 30));

        assertThat(values(created.factors().getFirst().id())).isEqualTo(Map.of("periodFromMonth", "1", "periodToMonth", "6", "minOverdueDays", "30"));
        assertThat(created.name()).isEqualTo(created.factors().getFirst().name()).isEqualTo(
                "Количество непрерывной просроченной задолженности по основному долгу и/или процентам с 1 по 6 месяц, "
                        + "где максимальная просрочка >= 30");
    }

    @Test
    void newParamsAreANewVersionAndTheOldVersionKeepsItsOwn() {
        StopFactorResponse created = createOverdueGroup(Map.of("periodFromMonth", 1, "periodToMonth", 6, "minOverdueDays", 30));

        StopFactorResponse updated = update(created, Map.of("periodFromMonth", 1, "periodToMonth", 6, "minOverdueDays", 30, "maxOverdueDays", 60));

        assertThat(updated.versionNumber()).isEqualTo(2);
        assertThat(values(updated.factors().getFirst().id())).containsEntry("maxOverdueDays", "60").hasSize(4);
        assertThat(values(created.factors().getFirst().id())).doesNotContainKey("maxOverdueDays").hasSize(3);
    }

    @Test
    void sameParamsCreateNoNewVersion() {
        StopFactorResponse created = createOverdueGroup(Map.of("periodFromMonth", 1, "periodToMonth", 6, "minOverdueDays", 30));

        StopFactorResponse updated = update(created, Map.of("periodFromMonth", "1", "periodToMonth", 6, "minOverdueDays", 30.0));

        assertThat(updated.versionNumber()).isEqualTo(1);
    }

    @Test
    void aMetricParamLabelChangeIsANewMetricVersion() {
        MetricDto metric = metricConsoleService.get(OVERDUE_METRIC);

        MetricDto updated = metricConsoleService.update(OVERDUE_METRIC, withParams(metric, metric.params().stream()
                .map(p -> p.key().equals("minOverdueDays") ? new MetricParamSchema(p.key(), "Просрочка от, дней", p.required()) : p)
                .toList()));
        entityManager.flush();

        assertThat(updated.id()).isEqualTo(OVERDUE_METRIC);
        assertThat(updated.versionNumber()).isEqualTo(2);
        assertThat(metricConsoleService.get(OVERDUE_METRIC).params())
                .contains(new MetricParamSchema("minOverdueDays", "Просрочка от, дней", true));
        assertThat(metricConsoleService.getVersions(OVERDUE_METRIC)).extracting(MetricDto::versionNumber).containsExactly(2, 1);
    }

    @Test
    void earlierGroupVersionsKeepTheMetricAsItWasWhenItChanges() {
        MetricDto metric = metricConsoleService.create(MetricDto.of("Тест: просрочки", OVERDUE_BUCKET_COUNT,
                "Тест {periodFromMonth}-{periodToMonth}: {minOverdueDays}", null, true, null));
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(metric.id(), Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10))),
                null));
        StopFactorResponse withoutMax = update(created, Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1));
        entityManager.flush();

        // maxOverdueDays is removed from the metric: the first group version still has it, with metric version 1
        List<MetricParamSchema> params = metric.params().stream().filter(p -> !p.key().equals("maxOverdueDays")).toList();
        MetricDto renamed = metricConsoleService.update(metric.id(), MetricDto.of("Тест: просрочки, новая версия",
                OVERDUE_BUCKET_COUNT, metric.nameTemplate(), params, true, "Верхняя граница не нужна"));
        entityManager.flush();

        assertThat(renamed.versionNumber()).isEqualTo(2);
        List<StopFactorResponse> versions = stopFactorConsoleService.getVersions(created.groupId());
        assertThat(versions).extracting(StopFactorResponse::versionNumber).containsExactly(3, 2, 1);
        assertThat(versions.getFirst().changeReason()).isEqualTo("Метрика «Тест: просрочки, новая версия» изменена: Верхняя граница не нужна");
        assertThat(versions.getFirst().factors()).singleElement().satisfies(factor -> {
            assertThat(factor.metricId()).isEqualTo(metric.id());
            assertThat(factor.metricVersionId()).isEqualTo(renamed.versionId());
            assertThat(factor.metricTitle()).isEqualTo("Тест: просрочки, новая версия");
            assertThat(factor.params()).isEqualTo(withoutMax.factors().getFirst().params());
        });
        assertThat(versions.get(2).factors()).singleElement().satisfies(factor -> {
            assertThat(factor.metricVersionId()).isEqualTo(metric.versionId());
            assertThat(factor.metricTitle()).isEqualTo("Тест: просрочки");
            assertThat(factor.params()).containsEntry("maxOverdueDays", 10);
        });
        assertThat(metricConsoleService.getVersions(metric.id()).get(1).params()).extracting(MetricParamSchema::key)
                .contains("maxOverdueDays");
    }

    @Test
    void theHistoryAtAPastMomentShowsTheMetricAsItWasThen() throws InterruptedException {
        MetricDto metric = metricConsoleService.create(MetricDto.of("Тест: история метрики", OVERDUE_BUCKET_COUNT,
                "Тест {periodFromMonth}-{periodToMonth}: {minOverdueDays}", null, true, null));
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(metric.id(), Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1))), null));
        long checkedFactor = created.factors().getFirst().id();
        jdbcTemplate.update("""
                INSERT INTO stop_factors.stop_factor_check (id, created_date, scoring_id, stop_factor_id, checked_value, result)
                VALUES (nextval('stop_factors.stop_factor_check_id_generator'), now(), -9001, ?, '0.0', 'APPROVED')
                """, checkedFactor);
        entityManager.flush();
        Thread.sleep(5);
        LocalDateTime before = LocalDateTime.now();
        Thread.sleep(5);

        metricConsoleService.update(metric.id(), MetricDto.of("Тест: история метрики, новое название", OVERDUE_BUCKET_COUNT,
                metric.nameTemplate(), metric.params(), true, "Переименована"));
        entityManager.flush();

        // now: the group follows the new metric version
        assertThat(stopFactorConsoleService.get(created.groupId()).factors().getFirst().metricTitle())
                .isEqualTo("Тест: история метрики, новое название");
        // at the earlier moment: the group version and the metric version valid then
        StopFactorResponse then = stopFactorConsoleService.getAll(null, metric.id(), null, null, before).stream()
                .filter(g -> g.groupId() == created.groupId()).findFirst().orElseThrow();
        assertThat(then.versionNumber()).isEqualTo(1);
        assertThat(then.factors().getFirst().metricTitle()).isEqualTo("Тест: история метрики");
        assertThat(then.factors().getFirst().metricVersionId()).isEqualTo(metric.versionId());
        assertThat(metricConsoleService.getAll(before)).filteredOn(m -> m.id().equals(metric.id()))
                .singleElement().extracting(MetricDto::title, MetricDto::versionNumber)
                .containsExactly("Тест: история метрики", 1);
        // a check made before the change keeps the metric version it was made with
        assertThat(stopFactorService.findByScoringId(-9001L)).singleElement().satisfies(row -> {
            assertThat(row.getFactorId()).isEqualTo(checkedFactor);
            assertThat(row.getMetricId()).isEqualTo(metric.id());
            assertThat(row.getMetricTitle()).isEqualTo("Тест: история метрики");
            assertThat(row.getMetricVersionNumber()).isEqualTo(1);
        });
    }

    @Test
    void aMetricChangeThatBreaksACurrentStopFactorIsRefused() {
        MetricDto metric = metricConsoleService.create(MetricDto.of("Тест: просрочки с верхней границей", OVERDUE_BUCKET_COUNT,
                "Тест {periodFromMonth}-{periodToMonth}: {minOverdueDays}", null, true, null));
        stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(metric.id(), Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10))),
                null));
        entityManager.flush();

        List<MetricParamSchema> withoutMax = metric.params().stream().filter(p -> !p.key().equals("maxOverdueDays")).toList();
        assertThatThrownBy(() -> metricConsoleService.update(metric.id(), withParams(metric, withoutMax)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("стоп-фактор 1: параметр «maxOverdueDays» не задан в метрике");
    }

    @Test
    void aMetricChangeNeedsAReasonAndTheActiveFlagAloneIsNoNewVersion() {
        MetricDto metric = metricConsoleService.get(OVERDUE_METRIC);

        assertThatThrownBy(() -> metricConsoleService.update(OVERDUE_METRIC, MetricDto.of("Новое название", metric.calculator(),
                metric.nameTemplate(), metric.params(), true, " ")))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Укажите причину изменения метрики");

        MetricDto deactivated = metricConsoleService.update(OVERDUE_METRIC, MetricDto.of(metric.title(), metric.calculator(),
                metric.nameTemplate(), metric.params(), false, null));
        assertThat(deactivated.versionNumber()).isEqualTo(metric.versionNumber());
        assertThat(deactivated.active()).isFalse();
    }

    @Test
    void anUnusedMetricParamIsRemoved() {
        MetricDto metric = metricConsoleService.create(MetricDto.of("Тест: просрочки без верхней границы", OVERDUE_BUCKET_COUNT,
                "Тест {periodFromMonth}-{periodToMonth}: {minOverdueDays}", null, true, null));
        List<MetricParamSchema> withoutMax = metric.params().stream().filter(p -> !p.key().equals("maxOverdueDays")).toList();

        MetricDto updated = metricConsoleService.update(metric.id(), withParams(metric, withoutMax));
        entityManager.flush();

        assertThat(updated.params()).extracting(MetricParamSchema::key)
                .containsExactly("periodFromMonth", "periodToMonth", "minOverdueDays");
        assertThat(count("SELECT jsonb_array_length(params) FROM stop_factors.stop_factor_metric WHERE id = " + updated.versionId()))
                .isEqualTo(3);
    }

    // ------------------------------------------------------------------------------------------------
    // Groups of different metrics
    // ------------------------------------------------------------------------------------------------

    @Test
    void aGroupMayHaveStopFactorsOfDifferentMetrics() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest("КИБ: споры", null, true,
                List.of(new StopFactorDto(ACTIVE_DISPUTES_METRIC, Map.of(), List.of(new ConditionDto(null, REJECTED, INTEGER, "0"))),
                        new StopFactorDto(UNJUSTIFIED_DISPUTES_METRIC, Map.of(), List.of(new ConditionDto(null, REJECTED, INTEGER, "1")))),
                null));
        entityManager.flush();

        assertThat(created.name()).isEqualTo("Активные споры КИБ, Необоснованные споры КИБ");
        assertThat(created.factors()).extracting(StopFactorDto::metricId, StopFactorDto::metricTitle).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ACTIVE_DISPUTES_METRIC, "Активные споры КИБ"),
                org.assertj.core.groups.Tuple.tuple(UNJUSTIFIED_DISPUTES_METRIC, "Необоснованные споры КИБ"));
        assertThat(stopFactorConsoleService.getAll(null, UNJUSTIFIED_DISPUTES_METRIC, null, null, null))
                .extracting(StopFactorResponse::groupId).contains(created.groupId());
    }

    @Test
    void theSameParamsOfDifferentMetricsAreNoRepeat() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(new StopFactorDto(ACTIVE_DISPUTES_METRIC, Map.of(), List.of()),
                        new StopFactorDto(UNJUSTIFIED_DISPUTES_METRIC, Map.of(), List.of())), null));

        assertThat(created.factors()).hasSize(2);
    }

    @Test
    void aStopFactorNeedsAKnownMetric() {
        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(new StopFactorDto(null, Map.of(), List.of()), new StopFactorDto(999_999L, Map.of(), List.of())), null)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Стоп-фактор 1: укажите метрику.")
                .hasMessageContaining("Стоп-фактор 2: метрика с id 999999 не найдена.");
    }

    @Test
    void aDisabledMetricCannotBeGivenToANewStopFactor() {
        MetricDto metric = metricConsoleService.get(ACTIVE_DISPUTES_METRIC);
        metricConsoleService.update(ACTIVE_DISPUTES_METRIC, MetricDto.of(metric.title(), metric.calculator(), metric.nameTemplate(),
                metric.params(), false, null));

        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(new StopFactorDto(ACTIVE_DISPUTES_METRIC, Map.of(), List.of())), null)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Стоп-фактор 1: метрика «Активные споры КИБ» отключена");
        // the existing group #2 keeps its stop factor and can still be changed
        StopFactorResponse disputes = stopFactorConsoleService.get(2);
        StopFactorResponse renamed = stopFactorConsoleService.update(2, new StopFactorRequest("Споры", disputes.orderNumber(),
                disputes.active(), disputes.factors(), "Описание"));
        assertThat(renamed.versionNumber()).isEqualTo(disputes.versionNumber() + 1);
    }

    // ------------------------------------------------------------------------------------------------
    // Several stop factors
    // ------------------------------------------------------------------------------------------------

    @Test
    void aGroupWithSeveralStopFactors() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest("Тест", null, true,
                List.of(
                factor(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 30)),
                new StopFactorDto(null, OVERDUE_METRIC, null, null, "Свое имя стоп-фактора",
                        Map.of("periodFromMonth", 4, "periodToMonth", 6, "minOverdueDays", 30),
                        List.of(new ConditionDto(null, UNDERWRITER, INTEGER, "0")), null, null, null)), null));
        entityManager.flush();

        assertThat(created.name()).isEqualTo("Непрерывная просроченная задолженность");
        assertThat(created.factors()).extracting(StopFactorDto::name).containsExactly(
                "Количество непрерывной просроченной задолженности по основному долгу и/или процентам с 1 по 3 месяц, "
                        + "где максимальная просрочка >= 30",
                "Свое имя стоп-фактора");
        assertThat(count("SELECT count(*) FROM stop_factors.stop_factor WHERE group_id = " + created.versionId())).isEqualTo(2);
    }

    @Test
    void twoStopFactorsWithTheSameParamsAreRefused() {
        Map<String, Object> params = Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 30);

        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(params), factor(params)), null)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Стоп-фактор 2 повторяет стоп-фактор 1: у них одинаковые метрика, параметры");
        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(), null)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Добавьте хотя бы один стоп-фактор");
    }

    @Test
    void changingOneStopFactorIsANewVersionWithAllStopFactorsAndTheOldVersionKeepsItsOwn() {
        long groupId = overdueGroup("RETAIL");
        StopFactorResponse current = stopFactorConsoleService.get(groupId);
        List<StopFactorDto> factors = new java.util.ArrayList<>(current.factors());
        StopFactorDto first = factors.getFirst();
        factors.set(0, new StopFactorDto(null, first.metricId(), null, null, null, first.params(),
                List.of(new ConditionDto(null, REJECTED, INTEGER, "6")), first.active(), first.productIds(), first.segment()));

        StopFactorResponse updated = stopFactorConsoleService.update(groupId, new StopFactorRequest(current.description(),
                current.orderNumber(), current.active(), factors, "Новый порог стоп-фактора 1"));
        entityManager.flush();

        assertThat(updated.versionNumber()).isEqualTo(current.versionNumber() + 1);
        assertThat(updated.factors()).hasSize(27);
        // every stop factor keeps its own segment and products
        assertThat(updated.factors()).extracting(StopFactorDto::segment, StopFactorDto::productIds)
                .containsExactlyElementsOf(current.factors().stream()
                        .map(f -> org.assertj.core.groups.Tuple.tuple(f.segment(), f.productIds())).toList());
        assertThat(updated.factors().getFirst().conditions()).singleElement().extracting(ConditionDto::value).isEqualTo("6");
        assertThat(stopFactorConsoleService.getVersions(groupId).get(1).factors().getFirst().conditions())
                .extracting(ConditionDto::value).containsExactly("5");
        assertThat(updated.name()).isEqualTo(current.name());
    }

    // ------------------------------------------------------------------------------------------------

    private static StopFactorDto factor(Map<String, Object> params) {
        return factor(OVERDUE_METRIC, params);
    }

    private static StopFactorDto factor(long metricId, Map<String, Object> params) {
        return new StopFactorDto(metricId, params, List.of(new ConditionDto(null, REJECTED, INTEGER, "0")));
    }

    private StopFactorResponse createOverdueGroup(Map<String, Object> params) {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(params)), null));
        entityManager.flush();
        return created;
    }

    private StopFactorResponse update(StopFactorResponse current, Map<String, Object> params) {
        long metricId = current.factors().getFirst().metricId();
        StopFactorResponse updated = stopFactorConsoleService.update(current.groupId(), new StopFactorRequest(current.description(),
                current.orderNumber(), current.active(), List.of(factor(metricId, params)), "Тест параметров"));
        entityManager.flush();
        return updated;
    }

    /** The 2026 overdue history group of a segment. */
    private long overdueGroup(String segment) {
        Long id = jdbcTemplate.queryForObject("""
                SELECT DISTINCT COALESCE(sf.root_id, sf.id) FROM stop_factors.stop_factor_group sf
                JOIN stop_factors.stop_factor f ON f.group_id = sf.id
                WHERE sf.valid_to IS NULL AND f.segment = ? AND sf.change_reason = 'Правила по просрочкам 2026'""",
                Long.class, segment);
        return id != null ? id : 0;
    }

    private static MetricDto withParams(MetricDto metric, List<MetricParamSchema> params) {
        return MetricDto.of(metric.title(), metric.calculator(), metric.nameTemplate(), params, metric.active(), "Параметры изменены");
    }

    /** Values of a stop factor as stored. */
    private Map<String, String> values(long factorId) {
        return jdbcTemplate.query("""
                        SELECT p.key, p.value FROM stop_factors.stop_factor f
                        CROSS JOIN LATERAL jsonb_each_text(f.params) AS p(key, value)
                        WHERE f.id = ?""",
                rs -> {
                    Map<String, String> values = new java.util.HashMap<>();
                    while (rs.next()) {
                        values.put(rs.getString(1), rs.getString(2));
                    }
                    return values;
                }, factorId);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
}
