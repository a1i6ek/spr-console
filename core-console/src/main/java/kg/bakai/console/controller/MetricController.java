package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.service.MetricConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Metrics", description = "What a stop factor measures and which params it sets. Edited in place: group "
        + "versions keep their own param values.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/metrics")
public class MetricController {

    private final MetricConsoleService metricService;

    @Operation(summary = "Metrics")
    @GetMapping
    public List<MetricDto> list() {
        return metricService.getAll();
    }

    @Operation(summary = "One metric")
    @GetMapping("/{metricId}")
    public MetricDto get(@PathVariable long metricId) {
        return metricService.get(metricId);
    }

    @Operation(summary = "Create a metric", description = "Params left out: every param of the calculator")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricDto create(@Valid @RequestBody MetricDto metric) {
        return metricService.create(metric);
    }

    @Operation(summary = "Change a metric", description = "Title, params, active; the calculator cannot change. Refused "
            + "when the new params would make a stop factor of a current group version invalid.")
    @PutMapping("/{metricId}")
    public MetricDto update(@PathVariable long metricId, @Valid @RequestBody MetricDto metric) {
        return metricService.update(metricId, metric);
    }

    @Operation(summary = "Delete a metric", description = "Only while no stop factor of any group version uses it")
    @DeleteMapping("/{metricId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long metricId) {
        metricService.delete(metricId);
    }
}
