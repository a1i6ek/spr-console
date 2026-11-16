package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.bakai.stopfactor.enums.CalculatorType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A metric: what a stop factor measures and the params it sets.
 *
 * @param id             in a response; ignored in a request
 * @param calculatorName in a response
 * @param params         left out when created: every param of the calculator
 * @param active         left out in a request: active; an inactive metric cannot be given to a new stop factor
 */
public record MetricDto(
        Long id,
        @NotBlank @Size(max = 255) String title,
        @NotNull CalculatorType calculator,
        String calculatorName,
        List<@NotNull @Valid MetricParam> params,
        Boolean active,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {
}
