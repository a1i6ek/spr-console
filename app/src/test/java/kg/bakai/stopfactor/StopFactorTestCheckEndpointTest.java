package kg.bakai.stopfactor;

import kg.bakai.console.test.StopFactorTestCheckService;
import kg.bakai.console.test.TestCheckRequest;
import kg.bakai.console.test.TestCheckResponse;
import kg.bakai.console.test.TestCheckScenario;
import kg.bakai.console.test.TestCheckScenarioResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static kg.bakai.stopfactor.enums.StopFactorStatus.APPROVED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test check endpoint on the local database (changelogs 1.1..1.5): every ready scenario gets its expected decision,
 * missing data is imitated as described, the endpoint accepts a partial request. Every test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StopFactorTestCheckEndpointTest {

    @Autowired
    private StopFactorTestCheckService testCheckService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------------------------------------
    // Relation data of changelog 1.5
    // ------------------------------------------------------------------------------------------------

    @Test
    void testStopFactorsAreLinkedToTestProducts() {
        List<String> links = jdbcTemplate.queryForList("""
                SELECT sf.name || ' -> ' || p.name
                FROM stop_factors.stop_factor_product sfp
                JOIN stop_factors.stop_factor sf ON sf.id = sfp.stop_factor_id AND sf.valid_to IS NULL
                JOIN public.product p ON p.id = sfp.product_id
                ORDER BY sf.id, p.id
                """, String.class);

        assertThat(links).containsExactly(
                "[Тест] Открытые кредиты: Ипотека и Автокредит -> Автокредит",
                "[Тест] Открытые кредиты: Ипотека и Автокредит -> Ипотека",
                "[Тест] Запросы в кредитное бюро за 7 дней: Микрокредит -> Микрокредит");
        assertThat(jdbcTemplate.queryForObject("SELECT segment_type FROM public.product WHERE id = 5", String.class))
                .isEqualTo("MSB");
    }

    // ------------------------------------------------------------------------------------------------
    // Scenarios
    // ------------------------------------------------------------------------------------------------

    @Test
    void everyScenarioGetsItsExpectedDecision() {
        for (TestCheckScenario scenario : testCheckService.scenarios()) {
            TestCheckScenarioResult result = testCheckService.runScenario(scenario.name());

            assertThat(result.asExpected())
                    .as("%s: expected %s %s, got %s %s, fired %s", scenario.name(),
                            scenario.expectedDecision(), scenario.expectedInternalStatusCode(),
                            result.result().decision(), result.result().internalStatusCode(), result.result().fired())
                    .isTrue();
        }
    }

    @Test
    void segmentOfTheProductChoosesTheRules() {
        assertThat(testCheckService.runScenario("retail-overdue-reject").result().segment()).isEqualTo(RETAIL);
        assertThat(testCheckService.runScenario("msb-same-history-yellow").result().segment()).isEqualTo(MSB);
    }

    // ------------------------------------------------------------------------------------------------
    // Missing data
    // ------------------------------------------------------------------------------------------------

    @Test
    void missingDataIsReportedWithHowTheCheckTreatsIt() {
        TestCheckResponse noKib = testCheckService.runScenario("missing-kib-report").result();
        assertThat(noKib.imitated()).anyMatch(s -> s.startsWith("Нет отчёта КИБ"));

        TestCheckResponse partial = testCheckService.runScenario("partial-kib-report").result();
        assertThat(partial.imitated()).anyMatch(s -> s.startsWith("Неполный отчёт КИБ, нет [CONSOLIDATION]"));

        TestCheckResponse withoutProduct = testCheckService.runScenario("scoring-without-product").result();
        assertThat(withoutProduct.productId()).isNull();
        assertThat(withoutProduct.segment()).isEqualTo(RETAIL);
        assertThat(withoutProduct.scoringId()).isNegative();
    }

    /** No payment calendars: no overdue (analyst's decision of 09.11.2026), no NO_DATA and no underwriter. */
    @Test
    void withoutPaymentCalendarsTheOverdueRulesCountNoOverdue() {
        TestCheckResponse result = testCheckService.runScenario("kib-calendars-unavailable").result();

        assertThat(result.decision()).isEqualTo(APPROVED);
        assertThat(result.checks()).noneMatch(c -> "NO_DATA".equals(c.getCheckedValue()));
        assertThat(result.imitated()).contains("В отчёте КИБ нет платёжных календарей: просрочки считаются отсутствующими");
    }

    @Test
    void requestCanLinkAScoringToAProduct() {
        TestCheckResponse result = testCheckService.check(new TestCheckRequest(-777L, 3L, null, null, null));

        assertThat(result.productName()).isEqualTo("Ипотека");
        assertThat(jdbcTemplate.queryForObject("SELECT product_id FROM public.scoring WHERE id = -777", Long.class)).isEqualTo(3L);
    }

    // ------------------------------------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------------------------------------

    @Test
    void endpointAcceptsAPartialRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/test/stop-factor-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "scoringId": 3, "kib": { "openCredits": 3, "overdueMonths": [ { "monthsAgo": 2, "maxOverdueDays": 12 } ] } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Ипотека"))
                .andExpect(jsonPath("$.decision").value("UNDERWRITER"))
                .andExpect(jsonPath("$.fired", hasItem("UNDERWRITER: [Тест] Открытые кредиты: Ипотека и Автокредит (3.0)")))
                .andExpect(jsonPath("$.imitated", hasItem("Нет кредитов в отчёте ОБ")));
    }

    @Test
    void emptyRequestIsAClientWithoutAnyData() throws Exception {
        mockMvc.perform(post("/api/v1/admin/test/stop-factor-check").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value(APPROVED.name()))
                .andExpect(jsonPath("$.segment").value("RETAIL"));
    }

    @Test
    void scenariosAreListedAndRunByName() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test/stop-factor-check/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("partial-kib-report")));

        mockMvc.perform(post("/api/v1/admin/test/stop-factor-check/scenarios/current-overdue-451"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asExpected").value(true))
                .andExpect(jsonPath("$.result.internalStatusCode").value("_451"));
    }

    @Test
    void swaggerPrefillsTheRequestWithEveryScenario() throws Exception {
        String examples = "$.paths['/api/v1/admin/test/stop-factor-check'].post.requestBody.content['application/json'].examples";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(examples + ".length()").value(testCheckService.scenarios().size()))
                .andExpect(jsonPath(examples + "['full-application'].summary").value("full-application → APPROVED"))
                .andExpect(jsonPath(examples + "['full-application'].value.scoringId").value(-100))
                .andExpect(jsonPath(examples + "['full-application'].value.requestDate").value("2026-11-04"))
                .andExpect(jsonPath(examples + "['full-application'].value.kib.overdueMonths[1].maxOverdueDays").value(12))
                .andExpect(jsonPath(examples + "['missing-kib-report'].value.kib").doesNotExist())
                .andExpect(jsonPath(examples + "['current-overdue-451'].summary").value("current-overdue-451 → REJECTED _451"));
    }

    @Test
    void unknownScenarioAndProductAreNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/test/stop-factor-check/scenarios/no-such-scenario"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/test/stop-factor-check").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"productId\": -999 }"))
                .andExpect(status().isNotFound());
    }
}
