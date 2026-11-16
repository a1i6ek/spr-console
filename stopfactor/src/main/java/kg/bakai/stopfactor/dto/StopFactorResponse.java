package kg.bakai.stopfactor.dto;

import kg.bakai.common.enums.ProductSegment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One version of a stop factor: the read model of the console. {@link #factorId} is the stop factor, the same for all
 * its versions; {@link #versionId} is its row in the first box this version was in.
 *
 * @param current      whether this is the version checks run now
 * @param validFrom    when this version was first checked with
 * @param validTo      until when; null for the current version
 * @param changeReason why this version was made
 * @param endReason    why the stop factor ended with this version (deleted); null otherwise
 * @param summary      generated from the metric and the params: the rule as it actually is, whatever the name says
 * @param checks       how many scorings were checked with this version, by result; only in the history
 */
public record StopFactorResponse(
        long factorId,
        long versionId,
        int versionNumber,
        boolean current,
        MetricRef metric,
        String name,
        String description,
        boolean active,
        ProductSegment segment,
        Map<String, Object> params,
        List<ConditionDto> conditions,
        Set<Long> productIds,
        String summary,
        int orderNumber,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String changeReason,
        String endReason,
        Checks checks
) {
    /** Scorings checked with a version: all of them, and how many were rejected or sent to the underwriter. */
    public record Checks(long total, long rejected, long underwriter) {
        public static final Checks NONE = new Checks(0, 0, 0);
    }

    public record MetricRef(long id, String title) {
    }
}
