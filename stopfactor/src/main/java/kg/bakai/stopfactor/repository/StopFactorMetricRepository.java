package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactorMetric;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Metric versions. A metric id is the id of its first version (StopFactorMetric#getMetricId). */
public interface StopFactorMetricRepository extends JpaRepository<StopFactorMetric, Long> {

    @Query("select m from StopFactorMetric m where m.validTo is null order by coalesce(m.rootId, m.id)")
    List<StopFactorMetric> findAllCurrent();

    /** The versions valid at a moment: valid_from <= at < valid_to. */
    @Query("""
            select m from StopFactorMetric m
            where m.validFrom <= :at and (m.validTo is null or m.validTo > :at)
            order by coalesce(m.rootId, m.id)
            """)
    List<StopFactorMetric> findAllAt(LocalDateTime at);

    @Query("""
            select m from StopFactorMetric m
            where m.validTo is null and ((m.id = :metricId and m.rootId is null) or m.rootId = :metricId)
            """)
    Optional<StopFactorMetric> findCurrent(long metricId);

    @Query("""
            select m from StopFactorMetric m
            where m.validTo is null and (m.id in :metricIds or m.rootId in :metricIds)
            """)
    List<StopFactorMetric> findAllCurrent(Collection<Long> metricIds);

    @Query("""
            select m from StopFactorMetric m
            where (m.id = :metricId and m.rootId is null) or m.rootId = :metricId
            order by m.versionNumber desc
            """)
    List<StopFactorMetric> findVersions(long metricId);

    // Every change of a metric first locks its first version, so concurrent edits run one after another
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from StopFactorMetric m where m.id = :metricId and m.rootId is null")
    Optional<StopFactorMetric> lockMetric(long metricId);
}
