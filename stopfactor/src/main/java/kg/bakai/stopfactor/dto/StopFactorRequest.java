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
 * A stop factor as the console sends it to create one or to change it. What the metric measures and which params it
 * takes comes from the metric; the factor only sets their values. A change makes the next version of this stop
 * factor alone; the versions before it stay for the checks made with them.
 *
 * @param metricId     what it measures
 * @param name         shown in the console and in the check results
 * @param active       left out: active
 * @param segment      RETAIL or MSB: only applications of products of this segment; null - every segment
 * @param params       values of the metric params, e.g. {"periodFromMonth": 7, "periodToMonth": 12}
 * @param conditions   at most one REJECTED and one UNDERWRITER condition; none - measured, but decides nothing
 * @param productIds   products it is limited to; empty or left out - every product of its segment
 * @param changeReason why: kept in the stop factor's history; required to change one, optional to create one
 */
public record StopFactorRequest(
        @NotNull Long metricId,
        @NotBlank @Size(max = 255) String name,
        String description,
        Boolean active,
        ProductSegment segment,
        Map<String, Object> params,
        List<@NotNull @Valid ConditionDto> conditions,
        Set<@NotNull Long> productIds,
        @Size(max = 255) String changeReason
) {
}
