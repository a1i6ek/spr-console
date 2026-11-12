package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.service.MetricConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Calculators", description = "What a metric can be built on")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/calculators")
public class CalculatorController {

    private final MetricConsoleService metricService;

    @Operation(summary = "Calculators", description = "Their params, allowed threshold value types and operators")
    @GetMapping
    public List<CalculatorDto> list() {
        return metricService.getCalculators();
    }
}
