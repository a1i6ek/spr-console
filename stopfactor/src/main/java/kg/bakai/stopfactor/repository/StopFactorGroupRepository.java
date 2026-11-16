package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactorGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/** The boxes of stop factors; one is current. */
public interface StopFactorGroupRepository extends JpaRepository<StopFactorGroup, Long> {

    @Query("SELECT g FROM StopFactorGroup g WHERE g.validTo IS NULL")
    Optional<StopFactorGroup> findCurrent();

    Optional<StopFactorGroup> findByVersionNumber(int versionNumber);

    // Every change first locks the first box, so concurrent changes run one after another and each one copies the box
    // the one before it made. The first box, not the current one: the current one stops being current once a change
    // commits
    @Query(value = "SELECT g.id FROM stop_factors.stop_factor_group g WHERE g.version_number = 1 FOR UPDATE", nativeQuery = true)
    Optional<Long> lock();
}
