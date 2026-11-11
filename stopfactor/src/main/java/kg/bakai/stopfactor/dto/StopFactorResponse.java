package kg.bakai.stopfactor.dto;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.enums.CalculatorType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record StopFactorResponse(
        long groupId,
        long versionId,
        int versionNumber,
        boolean current,
        String name,
        String description,
        boolean active,
        int orderNumber,
        long metricId,
        String metricTitle,
        CalculatorType calculator,
        Set<Long> productIds,
        ProductSegment segment,
        List<StopFactorDto> factors,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String changeReason,
        String createdBy,
        LocalDateTime createdDate
) {
}
