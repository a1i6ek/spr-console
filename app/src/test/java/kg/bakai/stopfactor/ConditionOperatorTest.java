package kg.bakai.stopfactor;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kg.bakai.stopfactor.enums.ConditionOperator.*;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Threshold operators: what the changelogs stored, what the console accepts and what the database allows.
 * Runs against the migrated database, every test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConditionOperatorTest {

    @Autowired
    private StopFactorConsoleService consoleService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------------------------------------
    // Changelogs
    // ------------------------------------------------------------------------------------------------

    @Test
    void migrationFilledTheOperatorOfEveryCondition() {
        assertThat(count("SELECT count(*) FROM stop_factors.stop_factor_condition")).isPositive();
        assertThat(count("SELECT count(*) FROM stop_factors.stop_factor_condition WHERE operator IS NULL")).isZero();
        // loan statuses are IN, numbers are not
        assertThat(count("""
                SELECT count(*) FROM stop_factors.stop_factor_condition
                WHERE (value_type IN ('ARRAY', 'STRING')) <> (operator = 'IN')""")).isZero();
        // a REJECTED number is "x > value"
        assertThat(count("""
                SELECT count(*) FROM stop_factors.stop_factor_condition
                WHERE result = 'REJECTED' AND value_type IN ('INTEGER', 'DOUBLE') AND operator <> 'GT'""")).isZero();
        // an UNDERWRITER number with a REJECTED number is RANGE up to exactly that REJECTED value
        assertThat(count("""
                SELECT count(*)
                FROM stop_factors.stop_factor_condition uw
                JOIN stop_factors.stop_factor_condition rej ON rej.stop_factor_id = uw.stop_factor_id AND rej.result = 'REJECTED'
                WHERE uw.result = 'UNDERWRITER' AND rej.value_type IN ('INTEGER', 'DOUBLE')
                  AND (uw.operator <> 'RANGE' OR uw.value_to <> rej.value)""")).isZero();
    }

    @Test
    void legacyConditionsKeepTheirIds() {
        List<String> statuses = jdbcTemplate.queryForList("""
                SELECT id || ' ' || result || ' ' || operator || ' ' || left(value, 10)
                FROM stop_factors.stop_factor_condition WHERE stop_factor_id = 1 ORDER BY result""", String.class);

        assertThat(statuses).containsExactly("2 REJECTED IN WrittenOff", "3 UNDERWRITER IN Transferre");
    }

    @Test
    void retailRuleSixToEightIsTheUnderwriterRange() {
        // changelog 1.4: months 7..12, overdue of 6..10 days - "до 5 - Ок, 6-8 - жёлтая зона, 9+ - отказ"
        List<String> conditions = jdbcTemplate.queryForList("""
                SELECT c.result || ' ' || c.operator || ' ' || c.value || ' ' || coalesce(c.value_to, '-')
                FROM stop_factors.stop_factor f
                JOIN stop_factors.stop_factor_group g ON g.id = f.group_id
                JOIN stop_factors.stop_factor_condition c ON c.stop_factor_id = f.id
                WHERE g.valid_to IS NULL AND f.segment = 'RETAIL'
                  AND f.params = '{"periodFromMonth": 7, "periodToMonth": 12, "minOverdueDays": 6, "maxOverdueDays": 10}'::jsonb
                ORDER BY c.result""", String.class);

        assertThat(conditions).containsExactly("REJECTED GT 8 -", "UNDERWRITER RANGE 5 8");
    }

    // ------------------------------------------------------------------------------------------------
    // Console
    // ------------------------------------------------------------------------------------------------

    @Test
    void everyNumberOperatorCanBeStored() {
        for (ConditionOperator operator : List.of(GT, GTE, LT, LTE, EQ, RANGE, BETWEEN)) {
            String valueTo = operator.hasValueTo() ? "20" : null;
            StopFactorDto created = create(operator, "10", valueTo, null, null, null);

            assertThat(created.conditions()).containsExactly(
                    new ConditionDto(StopFactorStatus.REJECTED, operator, "10", valueTo, INTEGER));
        }
    }

    @Test
    void operatorThatDoesNotFitIsAnError() {
        assertThatThrownBy(() -> create(IN, "8", null, null, null, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("оператор «in» не подходит для калькулятора");
    }

    @Test
    void valueToOnlyForRangeAndBetweenAndAboveValue() {
        assertThatThrownBy(() -> create(GTE, "8", "20", null, null, null))
                .hasMessageContaining("верхняя граница задаётся только для операторов");
        assertThatThrownBy(() -> create(BETWEEN, "8", null, null, null, null))
                .hasMessageContaining("укажите верхнюю границу для оператора «>= и <=»");
        assertThatThrownBy(() -> create(RANGE, "8", "8", null, null, null))
                .hasMessageContaining("верхняя граница (8) должна быть больше нижней (8)");
        assertThatThrownBy(() -> create(BETWEEN, "8", "9.5", null, null, null))
                .hasMessageContaining("верхняя граница: значение должно быть целым числом");
    }

    @Test
    void underwriterThatCanNeverFireIsAnError() {
        // every value above 7 is already >= 5
        assertThatThrownBy(() -> create(GTE, "5", null, GT, "7", null)).hasMessageContaining("(x > 7) никогда не сработает");
        assertThatThrownBy(() -> create(LT, "3", null, LTE, "2", null)).hasMessageContaining("никогда не сработает");
        assertThatThrownBy(() -> create(RANGE, "5", "8", BETWEEN, "6", "8")).hasMessageContaining("никогда не сработает");

        // overlapping but not covered: 3..5 is UNDERWRITER, above 5 REJECTED wins
        assertThat(create(GT, "5", null, BETWEEN, "3", "8")).isNotNull();
        // the same bound included in one and excluded in the other: 5 is UNDERWRITER
        assertThat(create(GT, "5", null, EQ, "5", null)).isNotNull();
    }

    @Test
    void sameValueToWrittenDifferentlyIsAccepted() {
        StopFactorDto created = create(GT, "8", null, RANGE, "5", "8.0");

        assertThat(created.conditions()).extracting(ConditionDto::valueTo).containsExactly(null, "8");
    }

    @Test
    void calculatorsListTheOperatorsForTheConsoleSelect() throws Exception {
        String json = mockMvc.perform(get("/api/v1/admin/calculators"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<Object>>read(json, "$[?(@.type == 'KIB_ACTIVE_DISPUTES')].allowedOperators[*]"))
                .containsExactly(
                        Map.of("code", "GT", "name", ">"),
                        Map.of("code", "GTE", "name", ">="),
                        Map.of("code", "LT", "name", "<"),
                        Map.of("code", "LTE", "name", "<="),
                        Map.of("code", "EQ", "name", "="),
                        Map.of("code", "RANGE", "name", "> и <="),
                        Map.of("code", "BETWEEN", "name", ">= и <="));
        assertThat(JsonPath.<List<Object>>read(json, "$[?(@.type == 'KIB_REFUSAL_STATUSES')].allowedOperators[*]"))
                .containsExactly(Map.of("code", "IN", "name", "in"));
    }

    // ------------------------------------------------------------------------------------------------
    // Database constraints
    // ------------------------------------------------------------------------------------------------

    @Test
    void databaseAcceptsOnlyConsistentConditions() {
        long id = create(GT, "8", null, RANGE, "5", "8").id();

        // RANGE and BETWEEN need value_to, the others none; numbers are not IN; the operator and result are known
        assertRejectedByDatabase(id, "UNDERWRITER", "operator = 'RANGE', value_to = NULL");
        assertRejectedByDatabase(id, "UNDERWRITER", "operator = 'GTE'");
        assertRejectedByDatabase(id, "REJECTED", "operator = 'IN'");
        assertRejectedByDatabase(id, "REJECTED", "operator = 'NE'");
        assertRejectedByDatabase(id, "REJECTED", "operator = NULL");
        assertRejectedByDatabase(id, "REJECTED", "result = 'APPROVED'");
        // one condition per result
        assertRejectedByDatabase(id, "UNDERWRITER", "result = 'REJECTED', operator = 'GT', value_to = NULL");
        // any operator for either result
        assertThat(jdbcTemplate.update("UPDATE stop_factors.stop_factor_condition SET operator = 'BETWEEN', value_to = '20' "
                + "WHERE stop_factor_id = ? AND result = 'REJECTED'", id)).isEqualTo(1);
    }

    /** The update fails on a constraint; a savepoint keeps the test transaction usable. */
    private void assertRejectedByDatabase(long stopFactorId, String result, String set) {
        jdbcTemplate.execute("SAVEPOINT constraint_check");
        try {
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE stop_factors.stop_factor_condition SET " + set
                    + " WHERE stop_factor_id = ? AND result = ?", stopFactorId, result))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT constraint_check");
        }
    }

    // ------------------------------------------------------------------------------------------------

    /** A group with an "active disputes" stop factor of these thresholds, flushed so plain SQL sees it. */
    private StopFactorDto create(ConditionOperator rejected, String rejectedValue, String rejectedValueTo,
                                 ConditionOperator underwriter, String underwriterValue, String underwriterValueTo) {
        StopFactorGroupDto created = consoleService.create(TestGroups.group(TestGroups.disputes(rejected, rejectedValue,
                rejectedValueTo, underwriter, underwriterValue, underwriterValueTo)));
        entityManager.flush();
        return created.factors().getFirst();
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
}
