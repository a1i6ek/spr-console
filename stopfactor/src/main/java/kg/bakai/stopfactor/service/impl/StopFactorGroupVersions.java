package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Versions of a stop factor group: every change closes the current version and starts the next one. */
@Component
@RequiredArgsConstructor
class StopFactorGroupVersions {

    private final StopFactorGroupRepository groupRepository;

    /** The current version of a group, after locking its first version so concurrent changes run one after another. */
    StopFactorGroup lockCurrent(long groupId) {
        groupRepository.lockGroup(groupId).orElseThrow(() -> notFound(groupId));
        return groupRepository.findCurrent(groupId).orElseThrow(() -> notFound(groupId));
    }

    /**
     * Closes the current version and returns the next one, not saved yet, with all stop factors copied.
     * The current version is flushed first: a group has one current version (ux_stop_factor_group_current_version)
     * and Hibernate would otherwise run the INSERT before the UPDATE.
     */
    StopFactorGroup next(StopFactorGroup current, String changeReason) {
        LocalDateTime now = LocalDateTime.now();
        current.setValidTo(now);
        groupRepository.saveAndFlush(current);

        StopFactorGroup next = current.newVersion(now, changeReason.trim());
        next.setCreatedBy("");
        return next;
    }

    static NotFoundException notFound(long groupId) {
        return new NotFoundException("Группа стоп-факторов №" + groupId + " не найдена.");
    }
}
