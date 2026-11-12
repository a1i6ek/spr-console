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
    // Products of changelog 1.2 and their stop factors of changelog 1.4
    // ------------------------------------------------------------------------------------------------

    @Test
    void productsAreLinkedToTheStopFactorsOfTheRequirements() {
        List<String> links = jdbcTemplate.queryForList("""
                SELECT p.id || ' ' || p.name || ' ' || p.segment_type || ': ' || count(f.id)
                FROM public.product p
                LEFT JOIN stop_factors.stop_factor_product sfp ON sfp.product_id = p.id
                LEFT JOIN stop_factors.stop_factor f ON f.id = sfp.stop_factor_id
                    AND f.group_id IN (SELECT id FROM stop_factors.stop_factor_group WHERE valid_to IS NULL)
                WHERE p.id BETWEEN 1 AND 8
                GROUP BY p.id ORDER BY p.id
                """, String.class);

        // Розница и ИФЦ: 14 overdue stop factors, МСБ: 10; the others are for every product, without links
        assertThat(links).containsExactly(
                "1 Online кредит RETAIL: 14",
                "2 Тумар RETAIL: 14",
                "3 Онлайн кредит для ИП MSB: 10",
                "4 Тунук онлайн RETAIL: 14",
                "5 Бизнес лимит MSB: 10",
                "6 Онлайн ИФЦ RETAIL: 14",
                "7 BakAi сплит RETAIL: 14",
                "8 Кредитная карта RETAIL: 14");
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

    @Test
    void overdueMonthsUnavailableSendNewRangesToTheUnderwriterAsNoData() {
        TestCheckResponse result = testCheckService.runScenario("kib-calendars-unavailable").result();

        assertThat(result.decision()).isEqualTo(UNDERWRITER);
        assertThat(result.checks()).anyMatch(c -> "NO_DATA".equals(c.getCheckedValue()));
        assertThat(result.imitated()).anyMatch(s -> s.startsWith("В отчёте КИБ нет платежных календарей"));
    }

    @Test
    void requestCanLinkAScoringToAProduct() {
        TestCheckResponse result = testCheckService.check(new TestCheckRequest(-777L, 3L, null, null, null));

        assertThat(result.productName()).isEqualTo("Онлайн кредит для ИП");
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
                                { "scoringId": 3, "kib": { "openCredits": 3, "overdueMonths": [
                                    { "monthsAgo": 2, "maxOverdueDays": 12 }, { "monthsAgo": 3, "maxOverdueDays": 15 } ] } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Онлайн кредит для ИП"))
                .andExpect(jsonPath("$.decision").value("UNDERWRITER"))
                // MSB, months 1..3, 11..20 days: 2 months - yellow zone; fired is named after the group
                .andExpect(jsonPath("$.fired", hasItem("UNDERWRITER: Непрерывная просроченная задолженность (2.0)")))
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
