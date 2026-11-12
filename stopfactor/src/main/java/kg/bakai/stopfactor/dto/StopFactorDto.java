package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.bakai.common.enums.ProductSegment;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A stop factor of a group: its metric with param values, its conditions and the applications it checks.
 *
 * @param id          in a response; ignored in a request (every group version has its own rows)
 * @param metricTitle in a response
 * @param active      left out in a request: active
 * @param segment     RETAIL or MSB: only applications of products of this segment; null - every segment
 * @param params      values of the metric params, e.g. {"periodFromMonth": 7, "periodToMonth": 12}
 * @param conditions  at most one REJECTED and one UNDERWRITER condition; none - measured, but decides nothing
 * @param productIds  products it is limited to; empty or left out - every product of its segment
 */
public record StopFactorDto(
        Long id,
        @NotNull Long metricId,
        String metricTitle,
        @NotBlank @Size(max = 255) String name,
        String description,
        Boolean active,
        ProductSegment segment,
        Map<String, Object> params,
        List<@NotNull @Valid ConditionDto> conditions,
        Set<@NotNull Long> productIds
) {
}
