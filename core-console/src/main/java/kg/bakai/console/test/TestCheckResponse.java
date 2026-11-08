package kg.bakai.console.test;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.enums.StopFactorStatus;

import java.util.List;

/**
 * @param segment   segment the stop factors were chosen by
 * @param imitated  what was missing or imitated in this application and how the check treats it
 * @param fired     stop factors that did not approve: "REJECTED: name (value)"
 * @param checks    every saved result, the same as GET /api/v1/stop-factors/checks/{scoringId}
 */
public record TestCheckResponse(
        long scoringId,
        Long productId,
        String productName,
        ProductSegment segment,
        StopFactorStatus decision,
        InternalStatusCode internalStatusCode,
        List<String> imitated,
        List<String> fired,
        List<StopFactorProjection> checks
) {
}
