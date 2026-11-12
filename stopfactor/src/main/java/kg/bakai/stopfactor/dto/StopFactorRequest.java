package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A stop factor group in the console. The group has no name of its own: the console shows it like its only stop
 * factor, or by the titles of its metrics.
 *
 * @param factors every stop factor is checked on its own, with its own metric, segment and products; a change of
 *                any of them creates a new version of the whole group
 */
public record StopFactorRequest(
        String description,
        @Min(0) Integer orderNumber,
        Boolean active,
        @NotEmpty List<@NotNull @Valid StopFactorDto> factors,
        @Size(max = 255) String changeReason
) {
}
