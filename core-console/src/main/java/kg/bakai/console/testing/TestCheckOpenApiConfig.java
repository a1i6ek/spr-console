package kg.bakai.console.testing;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prefills the request body of the test check in Swagger UI: every ready scenario is an example to pick,
 * "full-application" first. Examples come from {@link TestCheckScenarios}, so they are always valid requests
 * and the tests check their decisions.
 */
@Configuration
@ConditionalOnProperty(name = "stop-factor.test-check.enabled", havingValue = "true")
class TestCheckOpenApiConfig {

    static final String CHECK_PATH = "/api/v1/admin/test/stop-factor-check";

    // The JSON the endpoint reads: ISO dates, omitted fields instead of nulls
    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    @Bean
    OpenApiCustomizer testCheckExamples() {
        return openApi -> {
            PathItem path = openApi.getPaths() != null ? openApi.getPaths().get(CHECK_PATH) : null;
            if (path == null || path.getPost() == null || path.getPost().getRequestBody() == null) {
                return;
            }
            MediaType json = path.getPost().getRequestBody().getContent().get("application/json");
            if (json == null) {
                return;
            }
            Map<String, Example> examples = new LinkedHashMap<>();
            for (TestCheckScenario scenario : TestCheckScenarios.ALL) {
                String expected = scenario.expectedDecision()
                        + (scenario.expectedInternalStatusCode() != null ? " " + scenario.expectedInternalStatusCode() : "");
                examples.put(scenario.name(), new Example()
                        .summary(scenario.name() + " → " + expected)
                        .description(scenario.description())
                        .value(JSON.convertValue(scenario.request(), Map.class)));
            }
            json.setExamples(examples);
        };
    }
}
