package kg.bakai.console.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Local testing only, enabled by stop-factor.test-check.enabled=true. In SPR the check is not a REST endpoint:
 * SPR calls StopFactorService.check with the KIB / OB reports.
 */
@Tag(name = "Test check", description = "Runs the stop factor check with imitated KIB / OB data. Local testing only")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/test/stop-factor-check")
@ConditionalOnProperty(name = "stop-factor.test-check.enabled", havingValue = "true")
public class StopFactorTestCheckController {

    private final StopFactorTestCheckService testCheckService;

    @Operation(summary = "Check an application", description = "Every field is optional. kib = null imitates a client "
            + "without a KIB report, kib.missingParts a partial report, kib.calendarsUnavailable a KIB report without "
            + "payment calendars. Results are saved for the scoring like real ones.")
    @PostMapping
    public TestCheckResponse check(@Valid @RequestBody TestCheckRequest application) {
        return testCheckService.check(application);
    }

    @Operation(summary = "Ready test applications with their expected decisions")
    @GetMapping("/scenarios")
    public List<TestCheckScenario> listScenarios() {
        return testCheckService.scenarios();
    }

    @Operation(summary = "Run a ready test application", description = "asExpected tells whether the decision is the expected one")
    @PostMapping("/scenarios/{name}")
    public TestCheckScenarioResult runScenario(@PathVariable String name) {
        return testCheckService.runScenario(name);
    }
}
