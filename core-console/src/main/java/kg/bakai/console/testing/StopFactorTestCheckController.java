package kg.bakai.console.testing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local testing only, enabled by stop-factor.test-check.enabled=true. In SPR the check is not a REST endpoint:
 * SPR calls StopFactorService.check with the KIB / OB reports.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/test/stop-factor-check")
@ConditionalOnProperty(name = "stop-factor.test-check.enabled", havingValue = "true")
public class StopFactorTestCheckController {

    private final StopFactorTestCheckService testCheckService;

    @PostMapping
    public ResponseEntity<?> check(@Valid @RequestBody TestCheckRequest application) {
        return ResponseEntity.ok(testCheckService.check(application));
    }

    /** Ready test applications with their expected decisions. */
    @GetMapping("/scenarios")
    public ResponseEntity<?> listScenarios() {
        return ResponseEntity.ok(testCheckService.scenarios());
    }

    /** asExpected tells whether the decision is the expected one. */
    @PostMapping("/scenarios/{name}")
    public ResponseEntity<?> runScenario(@PathVariable String name) {
        return ResponseEntity.ok(testCheckService.runScenario(name));
    }
}
