package kg.bakai.console.controller;

import kg.bakai.console.test.StopFactorTestCheckService;
import kg.bakai.console.test.TestCheckRequest;
import kg.bakai.console.test.TestCheckResponse;
import kg.bakai.console.test.TestCheckScenario;
import kg.bakai.console.test.TestCheckScenarioResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LOCAL TESTING ONLY, enabled by stop-factor.test-check.enabled=true. In SPR the check is not a REST endpoint:
 * SPR calls StopFactorService.check with the KIB / OB reports.
 */
@Tag(name = "Test: stop factor check", description = "Runs StopFactorService.check with imitated KIB / OB data. Local testing only")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/test/stop-factor-check")
@ConditionalOnProperty(name = "stop-factor.test-check.enabled", havingValue = "true")
public class StopFactorTestCheckController {

    private final StopFactorTestCheckService testCheckService;

    @Operation(summary = "Check an application", description = "Every field is optional. kib = null imitates a client "
            + "without a KIB report, kib.missingParts a partial report, kib.calendarsUnavailable a KIB report "
            + "without payment calendars. Results are saved for the scoring like real ones.")
    @PostMapping
    public TestCheckResponse check(@Valid @RequestBody TestCheckRequest request) {
        return testCheckService.check(request);
    }

    @Operation(summary = "Ready test applications with their expected decisions")
    @GetMapping("/scenarios")
    public List<TestCheckScenario> scenarios() {
        return testCheckService.scenarios();
    }

    @Operation(summary = "Run a ready test application", description = "asExpected tells whether the decision matches the expected one")
    @PostMapping("/scenarios/{name}")
    public TestCheckScenarioResult runScenario(@PathVariable String name) {
        return testCheckService.runScenario(name);
    }
}
