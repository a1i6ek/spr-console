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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin console over HTTP: products, groups with their stop factors, metrics. Every test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StopFactorConsoleEndpointTest {

    private static final String GROUPS = "/api/v1/admin/stop-factor-groups";
    private static final String METRICS = "/api/v1/admin/metrics";
    /** Overdue history of months %d..%d rejecting above %s, limited to RETAIL applications of product 1. */
    private static final String OVERDUE = """
            { "metricId": 10, "name": "Просрочки с %1$d по %2$d месяц",
              "params": { "periodFromMonth": %1$d, "periodToMonth": %2$d, "minOverdueDays": 30 },
              "conditions": [ { "result": "REJECTED", "operator": "GT", "value": "%3$s", "valueType": "INTEGER" } ],
              "segment": "RETAIL", "productIds": [ 1 ] }""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void productsAreReadOnlyWithSprsSegmentAndTheStopFactorSegment() throws Exception {
        String businessLimit = call(get("/api/v1/admin/products/5"), status().isOk());
        assertThat(JsonPath.<String>read(businessLimit, "$.segmentType"))
                .isEqualTo(jdbcTemplate.queryForObject("SELECT segment_type FROM public.product WHERE id = 5", String.class));
        assertThat(JsonPath.<String>read(businessLimit, "$.segment")).isEqualTo("MSB");
        assertThat(JsonPath.<String>read(call(get("/api/v1/admin/products/6"), status().isOk()), "$.segment")).isEqualTo("RETAIL");
        call(get("/api/v1/admin/products/999999"), status().isNotFound());
    }

    @Test
    void theGroupsOfTheRequirementsAreListedInOrder() throws Exception {
        String all = call(get(GROUPS), status().isOk());

        assertThat(JsonPath.<List<String>>read(all, "$[*].name")).containsExactly(
                "Статусы и споры КИБ", "Реструктуризации", "Кредитная активность", "Текущая просрочка", "История просрочек");
        assertThat(JsonPath.<List<Integer>>read(all, "$[*].versionNumber")).containsExactly(1, 1, 2, 1, 2);
        assertThat(JsonPath.<List<Object>>read(all, "$[*].factors[*]")).hasSize(39);
    }

    @Test
    void aChangedStopFactorIsANewGroupVersionAndTheOldVersionKeepsItsRules() throws Exception {
        String created = call(post(GROUPS).contentType(MediaType.APPLICATION_JSON).content("""
                { "name": "Тест просрочек", "factors": [ %s ] }""".formatted(OVERDUE.formatted(1, 3, "2"))), status().isCreated());
        long groupId = id(created, "$.groupId");
        long firstFactor = id(created, "$.factors[0].id");
        assertThat(JsonPath.<Integer>read(created, "$.versionNumber")).isEqualTo(1);

        String changed = call(put(GROUPS + "/" + groupId + "/stop-factors/" + firstFactor).param("changeReason", "Новый порог")
                .contentType(MediaType.APPLICATION_JSON).content(OVERDUE.formatted(1, 3, "5")), status().isOk());

        assertThat(JsonPath.<Integer>read(changed, "$.versionNumber")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(changed, "$.factors[0].conditions[*].value")).containsExactly("5");
        assertThat(id(changed, "$.factors[0].id")).isNotEqualTo(firstFactor);
        // the rule of version 1 is not changed: checks made with it still show "2"
        assertThat(jdbcTemplate.queryForObject("SELECT value FROM stop_factors.stop_factor_condition WHERE stop_factor_id = ?",
                String.class, firstFactor)).isEqualTo("2");
        String versions = call(get(GROUPS + "/" + groupId + "/versions"), status().isOk());
        assertThat(JsonPath.<List<Integer>>read(versions, "$[*].versionNumber")).containsExactly(2, 1);
        assertThat(JsonPath.<List<Boolean>>read(versions, "$[*].current")).containsExactly(true, false);
        assertThat(JsonPath.<String>read(versions, "$[0].changeReason")).isEqualTo("Новый порог");
    }

    @Test
    void stopFactorsAreAddedAndRemovedAndTheOnlyOneStays() throws Exception {
        String created = call(post(GROUPS).contentType(MediaType.APPLICATION_JSON).content("""
                { "name": "Тест просрочек", "factors": [ %s ] }""".formatted(OVERDUE.formatted(1, 3, "2"))), status().isCreated());
        long groupId = id(created, "$.groupId");

        String added = call(post(GROUPS + "/" + groupId + "/stop-factors").param("changeReason", "Второй период")
                .contentType(MediaType.APPLICATION_JSON).content(OVERDUE.formatted(4, 6, "2")), status().isOk());
        assertThat(JsonPath.<List<Integer>>read(added, "$.factors[*].params.periodFromMonth")).containsExactly(1, 4);

        String removed = call(delete(GROUPS + "/" + groupId + "/stop-factors/" + id(added, "$.factors[1].id"))
                .param("changeReason", "Лишний период"), status().isOk());
        assertThat(JsonPath.<List<Object>>read(removed, "$.factors")).hasSize(1);
        assertThat(JsonPath.<Integer>read(removed, "$.versionNumber")).isEqualTo(3);

        call(delete(GROUPS + "/" + groupId + "/stop-factors/" + id(removed, "$.factors[0].id")).param("changeReason", "Тест"),
                status().isUnprocessableContent());
    }

    @Test
    void aDeletedGroupKeepsItsVersionsAndAChangeNeedsAReason() throws Exception {
        String created = call(post(GROUPS).contentType(MediaType.APPLICATION_JSON).content("""
                { "name": "Тест просрочек", "factors": [ %s ] }""".formatted(OVERDUE.formatted(1, 3, "2"))), status().isCreated());
        long groupId = id(created, "$.groupId");

        call(patch(GROUPS + "/" + groupId + "/status").param("active", "false").param("changeReason", " "),
                status().isUnprocessableContent());
        call(delete(GROUPS + "/" + groupId).param("changeReason", "Больше не нужна"), status().isNoContent());

        call(get(GROUPS + "/" + groupId), status().isNotFound());
        String versions = call(get(GROUPS + "/" + groupId + "/versions"), status().isOk());
        assertThat(JsonPath.<List<Boolean>>read(versions, "$[*].active")).containsExactly(false, true);
    }

    @Test
    void metricsAreCreatedChangedAndOnlyUnusedOnesDeleted() throws Exception {
        String created = call(post(METRICS).contentType(MediaType.APPLICATION_JSON).content("""
                { "title": "Запросы за период", "calculator": "BUREAU_REQUESTS_COUNT" }"""), status().isCreated());
        long metricId = id(created, "$.id");
        // params left out: the params of the calculator
        assertThat(JsonPath.<List<String>>read(created, "$.params[*].key")).containsExactly("days");

        String renamed = call(put(METRICS + "/" + metricId).contentType(MediaType.APPLICATION_JSON).content("""
                { "title": "Запросы в бюро за период", "calculator": "BUREAU_REQUESTS_COUNT",
                  "params": [ { "key": "days", "label": "Дней", "required": true } ] }"""), status().isOk());
        assertThat(JsonPath.<String>read(renamed, "$.params[0].label")).isEqualTo("Дней");

        call(delete(METRICS + "/" + metricId), status().isNoContent());
        // used by the legacy stop factors
        call(delete(METRICS + "/10"), status().isUnprocessableContent());
    }

    @Test
    void aMetricChangeThatBreaksACurrentStopFactorIsRefused() throws Exception {
        // the restructurings use "source"
        String refused = call(put(METRICS + "/4").contentType(MediaType.APPLICATION_JSON).content("""
                { "title": "Реструктуризации", "calculator": "RESTRUCTURING_COUNT",
                  "params": [ { "key": "periodFromMonth", "label": "С", "required": true },
                              { "key": "periodToMonth", "label": "По", "required": true } ] }"""), status().isUnprocessableContent());

        assertThat(refused).contains("обязателен для калькулятора");
    }

    @Test
    void calculatorsListTheirParamsAndOperators() throws Exception {
        String calculators = call(get("/api/v1/admin/calculators"), status().isOk());

        assertThat(JsonPath.<List<String>>read(calculators, "$[*].type")).hasSize(10).contains("OVERDUE_BUCKET_COUNT");
        assertThat(JsonPath.<List<String>>read(calculators, "$[?(@.type == 'KIB_REFUSAL_STATUSES')].allowedOperators[*].code"))
                .containsExactly("IN");
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private String call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        return mockMvc.perform(request)
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
