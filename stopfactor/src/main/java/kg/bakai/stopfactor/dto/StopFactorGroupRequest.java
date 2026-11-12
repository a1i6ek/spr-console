package kg.bakai.stopfactor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A group with all its stop factors. A change creates a new version of the group with new copies of every stop
 * factor; the previous version is closed and kept for the checks made with it.
 *
 * @param orderNumber  left out when created: after the other groups; left out when changed: kept
 * @param changeReason required when a group is changed, kept in the version history
 */
public record StopFactorGroupRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Min(0) Integer orderNumber,
        Boolean active,
        @NotEmpty List<@NotNull @Valid StopFactorDto> factors,
        @Size(max = 255) String changeReason
) {
}
