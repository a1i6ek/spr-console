package kg.bakai.console.controller;

import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.service.MetricConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Calculators: what a metric can be built on - their params, allowed threshold value types and operators. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/calculators")
public class CalculatorConsoleController {

    private final MetricConsoleService metricService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(metricService.getCalculators());
    }
}
