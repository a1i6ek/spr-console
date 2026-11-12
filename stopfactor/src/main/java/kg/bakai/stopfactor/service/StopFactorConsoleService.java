package kg.bakai.stopfactor.service;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;

import java.util.List;

/**
 * Stop factors in the console: the groups with their versions, and the reads and writes of a single stop factor
 * found by its id across the groups. A change of a group or of any of its stop factors creates a new version of
 * the group with copies of every stop factor; the previous version is closed and kept for the checks made with it.
 */
public interface StopFactorConsoleService {

    /**
     * Current versions of the groups, in display order.
     *
     * @param productId groups with a stop factor that checks applications of this product
     * @param segment   groups with a stop factor of this segment or of every segment
     * @param metricId  groups with a stop factor of this metric
     */
    List<StopFactorGroupDto> getAll(Boolean active, Long productId, ProductSegment segment, Long metricId);

    StopFactorGroupDto get(long groupId);

    /** Every version of a group, newest first. */
    List<StopFactorGroupDto> getVersions(long groupId);

    StopFactorGroupDto create(StopFactorGroupRequest request);

    /** A new version with these fields and stop factors; nothing is created when nothing differs. */
    StopFactorGroupDto update(long groupId, StopFactorGroupRequest request);

    StopFactorGroupDto changeStatus(long groupId, boolean active, String changeReason);

    /** Sets the display order of the groups: every group id in the new order. Changed in place, no new versions. */
    List<StopFactorGroupDto> reorder(List<Long> groupIds);

    /** Removes a group from the console and from checks: its versions stay for the checks made with them. */
    void delete(long groupId, String changeReason);

    /** Adds a stop factor after the others: a new version of the group. */
    StopFactorGroupDto addFactor(long groupId, StopFactorDto factor, String changeReason);

    /** Replaces a stop factor of the current version: a new version of the group. */
    StopFactorGroupDto updateFactor(long groupId, long factorId, StopFactorDto factor, String changeReason);

    /** Removes a stop factor of the current version: a new version of the group. */
    StopFactorGroupDto deleteFactor(long groupId, long factorId, String changeReason);

    /**
     * Stop factors of the current group versions, in group display order then factor order: how an admin finds a
     * factor without walking the groups one by one.
     *
     * @param groupId   only factors of this group
     * @param metricId  only factors of this metric
     * @param segment   factors of this segment or of every segment
     * @param productId factors that check applications of this product
     * @param active    only active or only inactive factors
     * @param search    in the name or the description, ignoring case
     */
    List<StopFactorResponse> searchFactors(Long groupId, Long metricId, ProductSegment segment, Long productId,
                                       Boolean active, String search);

    /** A stop factor with its group version and metric; factors of closed versions read the same way. */
    StopFactorResponse getFactor(long factorId);

    /** Replaces a stop factor found by its id: a new version of its group, validated like any group change. */
    StopFactorResponse updateFactor(long factorId, StopFactorRequest request, String changeReason);

    /** Turns a stop factor found by its id on or off: a new version of its group. */
    StopFactorResponse changeFactorStatus(long factorId, boolean active, String changeReason);
}
