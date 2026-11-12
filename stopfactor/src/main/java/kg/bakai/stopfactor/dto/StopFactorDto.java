package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.bakai.common.enums.ProductSegment;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A stop factor of a group: the metric it measures, the param values, the conditions that judge the value and the
 * segment and products it applies to. Stop factors of one group may measure different metrics.
 *
 * @param id              in a response; ignored in a request
 * @param metricId        the metric (the id of its first version); a request uses its current version
 * @param metricVersionId in a response: the metric version the stop factor was saved with
 * @param metricTitle     in a response: the title of that metric version
 * @param name            left out in a request, it is the metric's name template rendered with the params
 * @param params          values of the metric params, e.g. {"periodFromMonth": 7, "periodToMonth": 12}
 * @param conditions      at most one REJECTED and one UNDERWRITER condition
 * @param active          an inactive stop factor is not checked; left out in a request it is active
 * @param productIds      products it is limited to, empty or left out - every product
 * @param segment         segment of the products it applies to, null - every segment
 */
public record StopFactorDto(
        Long id,
        Long metricId,
        Long metricVersionId,
        String metricTitle,
        @Size(max = 255) String name,
        Map<String, Object> params,
        @NotNull List<@NotNull @Valid ConditionDto> conditions,
        Boolean active,
        Set<@NotNull Long> productIds,
        ProductSegment segment
) {

    /** An active stop factor of a request for every product: no id, the name rendered from the params. */
    public StopFactorDto(Long metricId, Map<String, Object> params, List<ConditionDto> conditions) {
        this(null, metricId, null, null, null, params, conditions, null, null, null);
    }

    /** The same stop factor with other conditions. */
    public StopFactorDto withConditions(List<ConditionDto> conditions) {
        return new StopFactorDto(id, metricId, metricVersionId, metricTitle, name, params, conditions, active, productIds, segment);
    }
}
