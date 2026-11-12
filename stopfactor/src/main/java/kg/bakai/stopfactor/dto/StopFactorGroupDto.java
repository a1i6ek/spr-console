package kg.bakai.stopfactor.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A version of a group with its stop factors.
 *
 * @param groupId   the group: the id of its first version, the same for all its versions
 * @param versionId this version
 * @param current   false for a closed version (validTo set)
 */
public record StopFactorGroupDto(
        long groupId,
        long versionId,
        int versionNumber,
        boolean current,
        String name,
        String description,
        boolean active,
        int orderNumber,
        List<StopFactorDto> factors,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String changeReason
) {
}
