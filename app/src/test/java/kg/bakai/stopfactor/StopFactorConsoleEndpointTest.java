package kg.bakai.stopfactor;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin console over HTTP: products, groups of stop factors, their stop factors and conditions. Every change
 * of a stop factor or a condition is a new version of the group. Every test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StopFactorConsoleEndpointTest {

    private static final String STOP_FACTORS = "/api/v1/admin/stop-factors";
    private static final String OVERDUE_FACTOR = """
            { "params": { "periodFromMonth": %d, "periodToMonth": %d, "minOverdueDays": 30 },
              "conditions": [ { "result": "REJECTED", "valueType": "INTEGER", "value": "0" } ] }""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void productsAreReadOnly() throws Exception {
        String all = call(get("/api/v1/admin/products"), status().isOk());
        assertThat(JsonPath.<List<Object>>read(all, "$[*].id")).isNotEmpty();

        String product = call(get("/api/v1/admin/products/5"), status().isOk());
        assertThat(JsonPath.<Object>read(product, "$.segmentType"))
                .isEqualTo(jdbcTemplate.queryForObject("SELECT segment_type FROM public.product WHERE id = 5", String.class));
        call(get("/api/v1/admin/products/999999"), status().isNotFound());
    }

    @Test
    void stopFactorsAreAddedChangedAndRemoved() throws Exception {
        long groupId = createOverdueGroup();

        String added = call(post(STOP_FACTORS + "/" + groupId + "/factors").param("changeReason", "Второй период")
                .contentType(MediaType.APPLICATION_JSON).content(OVERDUE_FACTOR.formatted(4, 6)), status().isOk());
        assertThat(JsonPath.<Integer>read(added, "$.versionNumber")).isEqualTo(2);
        assertThat(JsonPath.<List<Integer>>read(added, "$.factors[*].params.periodFromMonth")).containsExactly(1, 4);
        String firstName = JsonPath.read(added, "$.factors[0].name");

        // the second stop factor gets new params; the first keeps its name
        long secondFactor = factorId(added, 1);
        String updated = call(put(STOP_FACTORS + "/" + groupId + "/factors/" + secondFactor).param("changeReason", "Другой период")
                .contentType(MediaType.APPLICATION_JSON).content(OVERDUE_FACTOR.formatted(7, 12)), status().isOk());
        assertThat(JsonPath.<List<Integer>>read(updated, "$.factors[*].params.periodFromMonth")).containsExactly(1, 7);
        assertThat(JsonPath.<String>read(updated, "$.factors[0].name")).isEqualTo(firstName);
        assertThat(JsonPath.<String>read(updated, "$.factors[1].name")).contains("с 7 по 12 месяц");

        // a stop factor id of an older version is not found
        call(get(STOP_FACTORS + "/" + groupId + "/factors/" + secondFactor), status().isNotFound());

        String removed = call(delete(STOP_FACTORS + "/" + groupId + "/factors/" + factorId(updated, 1))
                .param("changeReason", "Лишний период"), status().isOk());
        assertThat(JsonPath.<List<Object>>read(removed, "$.factors")).hasSize(1);
        assertThat(JsonPath.<Integer>read(removed, "$.versionNumber")).isEqualTo(4);

        // the only stop factor stays
        call(delete(STOP_FACTORS + "/" + groupId + "/factors/" + factorId(removed, 0)).param("changeReason", "Тест"),
                status().isUnprocessableContent());
    }

    @Test
    void aStopFactorIsRenamed() throws Exception {
        long groupId = createOverdueGroup();
        String current = call(get(STOP_FACTORS + "/" + groupId), status().isOk());

        String renamed = call(put(STOP_FACTORS + "/" + groupId + "/factors/" + factorId(current, 0)).param("changeReason", "Имя")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "name": "Первый квартал", "params": { "periodFromMonth": 1, "periodToMonth": 3, "minOverdueDays": 30 },
                          "conditions": [ { "result": "REJECTED", "valueType": "INTEGER", "value": "0" } ] }"""), status().isOk());

        assertThat(JsonPath.<String>read(renamed, "$.factors[0].name")).isEqualTo("Первый квартал");
        assertThat(JsonPath.<Integer>read(renamed, "$.versionNumber")).isEqualTo(2);
    }

    @Test
    void conditionsOfAStopFactorAreSetAndRemoved() throws Exception {
        long groupId = createOverdueGroup();
        String current = call(get(STOP_FACTORS + "/" + groupId), status().isOk());
        String factor = STOP_FACTORS + "/" + groupId + "/factors/" + factorId(current, 0);

        String withUnderwriter = call(put(factor + "/conditions").param("changeReason", "Жёлтая зона")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "result": "UNDERWRITER", "valueType": "INTEGER", "value": "0", "operator": "EQ" }"""), status().isOk());
        String newFactor = STOP_FACTORS + "/" + groupId + "/factors/" + factorId(withUnderwriter, 0);
        String conditions = call(get(newFactor + "/conditions"), status().isOk());
        assertThat(JsonPath.<List<String>>read(conditions, "$[*].result")).containsExactlyInAnyOrder("REJECTED", "UNDERWRITER");

        // the REJECTED condition is replaced, not added
        String replaced = call(put(newFactor + "/conditions").param("changeReason", "Порог")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "result": "REJECTED", "valueType": "INTEGER", "value": "2", "operator": "GTE" }"""), status().isOk());
        assertThat(JsonPath.<List<String>>read(replaced, "$.factors[0].conditions[?(@.result == 'REJECTED')].value")).containsExactly("2");

        String withoutUnderwriter = call(delete(STOP_FACTORS + "/" + groupId + "/factors/" + factorId(replaced, 0) + "/conditions/UNDERWRITER")
                .param("changeReason", "Без жёлтой зоны"), status().isOk());
        assertThat(JsonPath.<List<String>>read(withoutUnderwriter, "$.factors[0].conditions[*].result")).containsExactly("REJECTED");
        assertThat(JsonPath.<Integer>read(withoutUnderwriter, "$.versionNumber")).isEqualTo(4);
    }

    @Test
    void aConditionChangeKeepsTheLegacyStopFactorNames() throws Exception {
        String restructurings = call(get(STOP_FACTORS + "/4"), status().isOk());
        List<String> names = JsonPath.read(restructurings, "$.factors[*].name");

        String changed = call(put(STOP_FACTORS + "/4/factors/" + factorId(restructurings, 1) + "/conditions").param("changeReason", "Порог")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "result": "REJECTED", "valueType": "INTEGER", "value": "3" }"""), status().isOk());

        assertThat(JsonPath.<List<String>>read(changed, "$.factors[*].name")).isEqualTo(names);
        assertThat(JsonPath.<String>read(changed, "$.name")).isEqualTo(JsonPath.read(restructurings, "$.name"));
    }

    @Test
    void aDeletedGroupKeepsItsVersions() throws Exception {
        long groupId = createOverdueGroup();

        call(delete(STOP_FACTORS + "/" + groupId).param("changeReason", "Больше не нужна"), status().isNoContent());

        call(get(STOP_FACTORS + "/" + groupId), status().isNotFound());
        String versions = call(get(STOP_FACTORS + "/" + groupId + "/versions"), status().isOk());
        assertThat(JsonPath.<List<Boolean>>read(versions, "$[*].active")).containsExactly(false, true);
        assertThat(JsonPath.<String>read(versions, "$[0].changeReason")).isEqualTo("Больше не нужна");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stop_factors.stop_factor_group WHERE valid_to IS NULL "
                + "AND COALESCE(root_id, id) = ?", Long.class, groupId)).isZero();
    }

    @Test
    void aChangeNeedsAReason() throws Exception {
        long groupId = createOverdueGroup();
        call(delete(STOP_FACTORS + "/" + groupId).param("changeReason", " "), status().isUnprocessableContent());
        call(delete(STOP_FACTORS + "/" + groupId), status().isBadRequest());
    }

    private long createOverdueGroup() throws Exception {
        String created = call(post(STOP_FACTORS).header("X-User", "admin").contentType(MediaType.APPLICATION_JSON).content("""
                { "metricId": 10, "segment": "RETAIL", "factors": [ %s ] }""".formatted(OVERDUE_FACTOR.formatted(1, 3))),
                status().isCreated());
        return ((Number) JsonPath.read(created, "$.groupId")).longValue();
    }

    private static long factorId(String stopFactor, int index) {
        return ((Number) JsonPath.read(stopFactor, "$.factors[" + index + "].id")).longValue();
    }

    private String call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        return mockMvc.perform(request.header("X-User", "admin"))
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
