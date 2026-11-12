package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.service.MetricConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Metrics", description = "What a stop factor measures and which params it takes")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/metrics")
public class MetricController {

    private final MetricConsoleService metricService;

    @Operation(summary = "Current metrics, or the metric versions valid at a moment")
    @GetMapping
    public List<MetricDto> list(
            @Parameter(description = "Moment of the history, e.g. 2026-11-01T00:00:00; left out - the current versions")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return metricService.getAll(at);
    }

    @Operation(summary = "Current version of a metric")
    @GetMapping("/{metricId}")
    public MetricDto get(@PathVariable long metricId) {
        return metricService.get(metricId);
    }

    @Operation(summary = "All versions of a metric, newest first")
    @GetMapping("/{metricId}/versions")
    public List<MetricDto> listVersions(@PathVariable long metricId) {
        return metricService.getVersions(metricId);
    }

    @Operation(summary = "Calculators a metric can be built on",
            description = "Their params, allowed condition value types and operators")
    @GetMapping("/calculators")
    public List<CalculatorDto> listCalculators() {
        return metricService.getCalculators();
    }

    @Operation(summary = "Create a metric", description = "Params left out are the params of the calculator")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricDto create(@Valid @RequestBody MetricDto metric) {
        return metricService.create(metric);
    }

    @Operation(summary = "Update a metric", description = "A new title, name template or params create a new metric "
            + "version (changeReason is required): every current group using the metric gets a new version with it, "
            + "earlier group versions and checks keep the metric version they were made with. The calculator cannot be "
            + "changed; the active flag alone is changed in place. Rejected when the new params would make a current "
            + "stop factor of this metric invalid; stop factor names are kept.")
    @PutMapping("/{metricId}")
    public MetricDto update(@PathVariable long metricId, @Valid @RequestBody MetricDto metric) {
        return metricService.update(metricId, metric);
    }

    @Operation(summary = "Delete a metric", description = "Only while no stop factor of any group version uses any of its "
            + "versions, otherwise deactivate it")
    @DeleteMapping("/{metricId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long metricId) {
        metricService.delete(metricId);
    }
}
