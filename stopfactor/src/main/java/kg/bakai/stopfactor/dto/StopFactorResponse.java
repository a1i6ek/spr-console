package kg.bakai.stopfactor.dto;

import kg.bakai.common.enums.ProductSegment;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A stop factor with where it lives: the read model of the stop factor console endpoints. Unlike
 * {@link StopFactorDto}, which a group carries inline, the group and the metric come as refs, so a list that spans
 * several groups shows what every factor belongs to. Factors of closed group versions read the same way; only
 * changes are limited to the current version.
 */
public record StopFactorResponse(
        Long id,
        String name,
        String description,
        boolean active,
        ProductSegment segment,
        Map<String, Object> params,
        List<ConditionDto> conditions,
        Set<Long> productIds,
        MetricRef metric,
        GroupRef group,
        /** Generated from the metric and the params: the rule as it actually is, whatever the name says. */
        String summary
) {

    /** What the factor measures. */
    public record MetricRef(Long id, String title) {
    }

    /** The group version the factor belongs to; groupId is the same for all versions of the group. */
    public record GroupRef(long groupId, long versionId, int versionNumber, String name,
                           boolean groupActive, int orderNumber) {
    }
}
