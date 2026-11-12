package kg.bakai.stopfactor.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StopFactorResponse(
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
        String changeReason,
        String createdBy,
        LocalDateTime createdDate
) {
}
