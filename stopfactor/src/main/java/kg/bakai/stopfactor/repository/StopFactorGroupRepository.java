package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.repository.projection.ProductProjection;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StopFactorGroupRepository extends JpaRepository<StopFactorGroup, Long> {

    /** Current versions of the active groups with their stop factors and metrics, in display order. */
    @Query("""
            select distinct g from StopFactorGroup g
            join fetch g.factors f
            join fetch f.metric
            where g.validTo is null and g.isActive = true
            order by g.orderNumber, g.id
            """)
    List<StopFactorGroup> findCurrentActiveWithFactorsAndMetric();

    @Query("""
            select distinct g from StopFactorGroup g
            join fetch g.factors f
            join fetch f.metric
            where g.validTo is null
            order by g.orderNumber, g.id
            """)
    List<StopFactorGroup> findAllCurrentWithFactorsAndMetric();

    @Query("""
            select distinct g from StopFactorGroup g
            left join fetch g.factors f
            left join fetch f.metric
            where g.validTo is null and ((g.id = :groupId and g.rootId is null) or g.rootId = :groupId)
            """)
    Optional<StopFactorGroup> findCurrent(long groupId);

    // Every change of a group first locks its first version, so concurrent edits run one after another.
    // The first version is locked, not the current one: the current one stops matching valid_to is null once an edit commits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from StopFactorGroup g where g.id = :groupId and g.rootId is null")
    Optional<StopFactorGroup> lockGroup(long groupId);

    @Query("""
            select distinct g from StopFactorGroup g
            left join fetch g.factors f
            left join fetch f.metric
            where (g.id = :groupId and g.rootId is null) or g.rootId = :groupId
            order by g.versionNumber desc
            """)
    List<StopFactorGroup> findVersions(long groupId);

    /** Current versions of the groups that have a stop factor of this metric. */
    @Query("""
            select distinct g from StopFactorGroup g
            join fetch g.factors f
            where g.validTo is null and f.metric.id = :metricId
            """)
    List<StopFactorGroup> findCurrentByMetricId(long metricId);

    /** Whether any version of any group has a stop factor of this metric. */
    @Query("select count(g) > 0 from StopFactorGroup g join g.factors f where f.metric.id = :metricId")
    boolean existsByMetricId(long metricId);

    /**
     * Product of the application being scored. Empty if the scoring has no product (legacy scorings) -
     * then product limits are ignored, like before they existed, and the default segment is used.
     * Tables of SPR in the public schema: scoring(id, product_id), product(id, name, description, segment_type).
     */
    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.description AS description, p.segment_type AS segmentType
            FROM public.scoring s
            JOIN public.product p ON p.id = s.product_id
            WHERE s.id = :scoringId
            """, nativeQuery = true)
    Optional<ProductProjection> findProductByScoringId(long scoringId);

    /**
     * Products of public.product (SPR) with these ids; ids without a product are not returned.
     */
    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.description AS description, p.segment_type AS segmentType
            FROM public.product p
            WHERE p.id IN (:productIds)
            """, nativeQuery = true)
    List<ProductProjection> findProductsByIds(Collection<Long> productIds);

    @Query(value = "SELECT COALESCE(MAX(order_number), 0) FROM stop_factors.stop_factor_group WHERE valid_to IS NULL",
            nativeQuery = true)
    int findMaxCurrentOrderNumber();
}
