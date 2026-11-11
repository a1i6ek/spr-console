package kg.bakai.stopfactor.service;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.enums.StopFactorStatus;

import java.util.List;

public interface StopFactorConsoleService {

    /**
     * @param productId groups that check applications of this product (its segment and product limits)
     * @param segment   groups of this segment, including the ones without a segment
     */
    List<StopFactorResponse> getAll(Boolean active, Long metricId, Long productId, ProductSegment segment);

    StopFactorResponse get(long groupId);

    List<StopFactorResponse> getVersions(long groupId);

    StopFactorResponse create(StopFactorRequest request, String user);

    StopFactorResponse update(long groupId, StopFactorRequest request, String user);

    StopFactorResponse changeStatus(long groupId, boolean active, String changeReason, String user);

    List<StopFactorResponse> reorder(List<Long> groupIds);

    /**
     * Removes a group from the console and from checks: a last, inactive version is created with the reason
     * and closed at once. The versions stay, so checks made with them still show their stop factors.
     */
    void delete(long groupId, String changeReason, String user);

    // Stop factors of the current version. Every change creates a new group version, so the stop factors get new
    // ids: take them from the returned group.

    List<StopFactorDto> getFactors(long groupId);

    StopFactorDto getFactor(long groupId, long factorId);

    /** Adds a stop factor after the others. */
    StopFactorResponse addFactor(long groupId, StopFactorDto factor, String changeReason, String user);

    /** Replaces a stop factor; a name left out is the metric's name template rendered with the params. */
    StopFactorResponse updateFactor(long groupId, long factorId, StopFactorDto factor, String changeReason, String user);

    StopFactorResponse deleteFactor(long groupId, long factorId, String changeReason, String user);

    List<ConditionDto> getConditions(long groupId, long factorId);

    /** Adds the condition of its result (REJECTED or UNDERWRITER) to a stop factor, or replaces it. */
    StopFactorResponse saveCondition(long groupId, long factorId, ConditionDto condition, String changeReason, String user);

    StopFactorResponse deleteCondition(long groupId, long factorId, StopFactorStatus result, String changeReason, String user);
}
