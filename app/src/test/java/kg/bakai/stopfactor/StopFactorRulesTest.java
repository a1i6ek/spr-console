package kg.bakai.stopfactor;

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
 * Stop factors grouped (changelog 1.8): stop_factor_group is the versioned unit, stop_factor the stop factor of a
 * group version with its param values (params JSON). Every test is rolled back.
 */
@SpringBootTest
@Transactional
class StopFactorRulesTest {

    private static final long OVERDUE_METRIC = 10;
    private static final long RESTRUCTURING_METRIC = 4;

    @Autowired
    private MetricConsoleService metricConsoleService;
    @Autowired
    private StopFactorConsoleService stopFactorConsoleService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    // ------------------------------------------------------------------------------------------------
    // Changelog 1.8
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

        // the legacy overdue history: version 1 with the legacy stop factors, version 2 inactive since the 2026 rules
        List<StopFactorResponse> overdue = stopFactorConsoleService.getVersions(13);
        assertThat(overdue).extracting(StopFactorResponse::versionNumber).containsExactly(2, 1);
        assertThat(overdue).allSatisfy(version -> {
            assertThat(version.name()).isEqualTo("Непрерывная просроченная задолженность");
            assertThat(version.factors()).hasSize(22);
        });
        assertThat(overdue.getFirst().active()).isFalse();
        assertThat(overdue.get(1).factors()).filteredOn(StopFactorDto::active).hasSize(17);
        assertThat(overdue.get(1).factors().getFirst().name()).endsWith("с 1 го по 3 месяц, где максимальная просрочка >= 1 и <= 15");

        // there are no separate groups #5..#7, #14..#34 any more
        assertThat(count("""
                SELECT count(*) FROM stop_factors.stop_factor_group
                WHERE COALESCE(root_id, id) BETWEEN 5 AND 7 OR COALESCE(root_id, id) BETWEEN 14 AND 34""")).isZero();
    }

    @Test
    void anInactiveStopFactorIsKeptButNotChecked() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(OVERDUE_METRIC, null, null, true,
                Set.of(), null, List.of(
                factor(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 30)),
                new StopFactorDto(null, null, Map.of("periodFromMonth", 4, "periodToMonth", 6, "minOverdueDays", 30),
                        List.of(new ConditionDto(null, REJECTED, INTEGER, "0")), false)), null), "test");

        assertThat(created.factors()).extracting(StopFactorDto::active).containsExactly(true, false);
    }

    @Test
    void the2026OverdueHistoryIsOneGroupPerSegment() {
        StopFactorResponse retail = stopFactorConsoleService.get(overdueGroup("RETAIL"));

        assertThat(retail.name()).isEqualTo("Непрерывная просроченная задолженность");
        assertThat(retail.factors()).hasSize(14);
        // stop factor 7: months 7..12, 6..10 days - "до 5 - Ок, 6-8 - жёлтая зона, 9+ - отказ"
        StopFactorDto factor = retail.factors().get(6);
        assertThat(factor.params()).isEqualTo(Map.of("periodFromMonth", 7, "periodToMonth", 12, "minOverdueDays", 6, "maxOverdueDays", 10));
        assertThat(factor.name()).endsWith("с 7 по 12 месяц, где максимальная просрочка >= 6 и <= 10");
        assertThat(factor.conditions()).containsExactlyInAnyOrder(
                new ConditionDto(factor.conditions().get(0).id(), REJECTED, INTEGER, "8", ConditionOperator.GT, null),
                new ConditionDto(factor.conditions().get(1).id(), UNDERWRITER, INTEGER, "5", ConditionOperator.RANGE, "8"));
        assertThat(stopFactorConsoleService.get(overdueGroup("MSB")).factors()).hasSize(14);
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
    void aMetricParamLabelChanges() {
        MetricDto metric = metricConsoleService.get(OVERDUE_METRIC);

        metricConsoleService.update(OVERDUE_METRIC, withParams(metric, metric.params().stream()
                .map(p -> p.key().equals("minOverdueDays") ? new MetricParamSchema(p.key(), "Просрочка от, дней", p.required()) : p)
                .toList()));
        entityManager.flush();

        assertThat(metricConsoleService.get(OVERDUE_METRIC).params())
                .contains(new MetricParamSchema("minOverdueDays", "Просрочка от, дней", true));
    }

    @Test
    void aMetricParamAnOldVersionUsesCannotBeRemoved() {
        MetricDto metric = metricConsoleService.create(new MetricDto(null, "Тест: просрочки", OVERDUE_BUCKET_COUNT, null,
                "Тест {periodFromMonth}-{periodToMonth}: {minOverdueDays}", null, true, null, null));
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(metric.id(), null, null, true,
                Set.of(), null, List.of(factor(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1, "maxOverdueDays", 10))),
                null), "test");
        update(created, Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 1));
        entityManager.flush();

        // the current version has no maxOverdueDays, the first one does
        List<MetricParamSchema> withoutMax = metric.params().stream().filter(p -> !p.key().equals("maxOverdueDays")).toList();
        assertThatThrownBy(() -> metricConsoleService.update(metric.id(), withParams(metric, withoutMax)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Параметры maxOverdueDays нельзя убрать");
    }

    @Test
    void anUnusedMetricParamIsRemoved() {
        MetricDto metric = metricConsoleService.create(new MetricDto(null, "Тест: просрочки без верхней границы", OVERDUE_BUCKET_COUNT,
                null, "Тест {periodFromMonth}-{periodToMonth}: {minOverdueDays}", null, true, null, null));
        List<MetricParamSchema> withoutMax = metric.params().stream().filter(p -> !p.key().equals("maxOverdueDays")).toList();

        MetricDto updated = metricConsoleService.update(metric.id(), withParams(metric, withoutMax));
        entityManager.flush();

        assertThat(updated.params()).extracting(MetricParamSchema::key)
                .containsExactly("periodFromMonth", "periodToMonth", "minOverdueDays");
        assertThat(count("SELECT jsonb_array_length(params) FROM stop_factors.stop_factor_metric WHERE id = " + metric.id())).isEqualTo(3);
    }

    // ------------------------------------------------------------------------------------------------
    // Several stop factors
    // ------------------------------------------------------------------------------------------------

    @Test
    void aGroupWithSeveralStopFactors() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(OVERDUE_METRIC, "Тест", null, true,
                Set.of(), null, List.of(
                factor(Map.of("periodFromMonth", 1, "periodToMonth", 3, "minOverdueDays", 30)),
                new StopFactorDto(null, "Свое имя стоп-фактора", Map.of("periodFromMonth", 4, "periodToMonth", 6, "minOverdueDays", 30),
                        List.of(new ConditionDto(null, UNDERWRITER, INTEGER, "0")))), null), "test");
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

        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(OVERDUE_METRIC, null, null, true,
                Set.of(), null, List.of(factor(params), factor(params)), null), "test"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Стоп-фактор 2 повторяет стоп-фактор 1: у них одинаковые параметры");
        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(OVERDUE_METRIC, null, null, true,
                Set.of(), null, List.of(), null), "test"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Добавьте хотя бы один стоп-фактор");
    }

    @Test
    void changingOneStopFactorIsANewVersionWithAllStopFactorsAndTheOldVersionKeepsItsOwn() {
        long groupId = overdueGroup("RETAIL");
        StopFactorResponse current = stopFactorConsoleService.get(groupId);
        List<StopFactorDto> factors = new java.util.ArrayList<>(current.factors());
        StopFactorDto first = factors.getFirst();
        factors.set(0, new StopFactorDto(null, null, first.params(), List.of(new ConditionDto(null, REJECTED, INTEGER, "6"))));

        StopFactorResponse updated = stopFactorConsoleService.update(groupId, new StopFactorRequest(current.metricId(),
                current.description(), current.orderNumber(), current.active(), current.productIds(), current.segment(),
                factors, "Новый порог стоп-фактора 1"), "test");
        entityManager.flush();

        assertThat(updated.versionNumber()).isEqualTo(current.versionNumber() + 1);
        assertThat(updated.factors()).hasSize(14);
        assertThat(updated.factors().getFirst().conditions()).singleElement().extracting(ConditionDto::value).isEqualTo("6");
        assertThat(stopFactorConsoleService.getVersions(groupId).get(1).factors().getFirst().conditions())
                .extracting(ConditionDto::value).containsExactly("5");
        assertThat(updated.name()).isEqualTo(current.name());
    }

    // ------------------------------------------------------------------------------------------------

    private static StopFactorDto factor(Map<String, Object> params) {
        return new StopFactorDto(params, List.of(new ConditionDto(null, REJECTED, INTEGER, "0")));
    }

    private StopFactorResponse createOverdueGroup(Map<String, Object> params) {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(OVERDUE_METRIC, null, null, true,
                Set.of(), null, List.of(factor(params)), null), "test");
        entityManager.flush();
        return created;
    }

    private StopFactorResponse update(StopFactorResponse current, Map<String, Object> params) {
        StopFactorResponse updated = stopFactorConsoleService.update(current.groupId(), new StopFactorRequest(current.metricId(),
                current.description(), current.orderNumber(), current.active(), current.productIds(),
                current.segment(), List.of(factor(params)), "Тест параметров"), "test");
        entityManager.flush();
        return updated;
    }

    /** The 2026 overdue history group of a segment. */
    private long overdueGroup(String segment) {
        Long id = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sf.root_id, sf.id) FROM stop_factors.stop_factor_group sf
                WHERE sf.valid_to IS NULL AND sf.segment = ? AND sf.change_reason = 'Правила по просрочкам 2026'""",
                Long.class, segment);
        return id != null ? id : 0;
    }

    private static MetricDto withParams(MetricDto metric, List<MetricParamSchema> params) {
        return new MetricDto(metric.id(), metric.title(), metric.calculator(), null, metric.nameTemplate(), params,
                metric.active(), null, null);
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
