package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.bakai.common.enums.ProductSegment;

import java.util.List;
import java.util.Set;

/**
 * A stop factor group in the console: a metric with one or more stop factors. The group has no name of its own -
 * the console shows it like its only stop factor, or like its metric when it has several of them.
 *
 * @param factors every stop factor is checked on its own, e.g. the overdue history of a segment by period and
 *                overdue days; a change of any of them creates a new version of the whole group
 */
public record StopFactorRequest(
        @NotNull Long metricId,
        String description,
        @Min(0) Integer orderNumber,
        Boolean active,
        Set<@NotNull Long> productIds,
        ProductSegment segment,
        @NotEmpty List<@NotNull @Valid StopFactorDto> factors,
        @Size(max = 255) String changeReason
) {
}
