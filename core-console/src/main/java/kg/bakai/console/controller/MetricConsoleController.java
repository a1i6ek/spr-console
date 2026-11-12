package kg.bakai.console.controller;

import jakarta.validation.Valid;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.service.MetricConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Metrics: what a stop factor measures and which params it sets. Edited in place: group versions keep their own
 * param values. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/metrics")
public class MetricConsoleController {

    private final MetricConsoleService metricService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(metricService.getAll());
    }

    @GetMapping("/{metricId}")
    public ResponseEntity<?> get(@PathVariable long metricId) {
        return ResponseEntity.ok(metricService.get(metricId));
    }

    /** Params left out: every param of the calculator. */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody MetricDto metric) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metricService.create(metric));
    }

    /** Title, params, active; the calculator cannot change. Refused when the new params would make a stop factor
     * of a current group version invalid. */
    @PutMapping("/{metricId}")
    public ResponseEntity<?> update(@PathVariable long metricId, @Valid @RequestBody MetricDto metric) {
        return ResponseEntity.ok(metricService.update(metricId, metric));
    }

    /** Only while no stop factor of any group version uses it. */
    @DeleteMapping("/{metricId}")
    public ResponseEntity<?> delete(@PathVariable long metricId) {
        metricService.delete(metricId);
        return ResponseEntity.noContent().build();
    }
}
