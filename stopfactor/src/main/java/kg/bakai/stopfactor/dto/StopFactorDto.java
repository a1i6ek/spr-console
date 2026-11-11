package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * A stop factor of a group: the param values its metric is measured with and the conditions that judge the value.
 *
 * @param id         in a response; ignored in a request
 * @param name       left out in a request, it is the metric's name template rendered with the params
 * @param params     values of the metric params, e.g. {"periodFromMonth": 7, "periodToMonth": 12}
 * @param conditions at most one REJECTED and one UNDERWRITER condition
 * @param active     an inactive stop factor is not checked; left out in a request it is active
 */
public record StopFactorDto(
        Long id,
        @Size(max = 255) String name,
        Map<String, Object> params,
        @NotNull List<@NotNull @Valid ConditionDto> conditions,
        Boolean active
) {

    /** An active stop factor. */
    public StopFactorDto(Long id, String name, Map<String, Object> params, List<ConditionDto> conditions) {
        this(id, name, params, conditions, null);
    }

    /** An active stop factor of a request: no id, the name rendered from the params. */
    public StopFactorDto(Map<String, Object> params, List<ConditionDto> conditions) {
        this(null, null, params, conditions, null);
    }
}
