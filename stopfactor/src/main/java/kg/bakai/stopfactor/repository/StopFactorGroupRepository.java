package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.repository.projection.ProductProjection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Group versions. A group id is the id of its first version. Stop factors, their metrics and products are loaded
 * lazily in batches (hibernate.default_batch_fetch_size), so callers run in a transaction.
 */
public interface StopFactorGroupRepository extends JpaRepository<StopFactorGroup, Long> {

    /** Current versions of every group, in display order. */
    @Query(value = """
            SELECT g.*
            FROM stop_factors.stop_factor_group g
            WHERE g.valid_to IS NULL
            ORDER BY g.order_number, g.id
            """, nativeQuery = true)
    List<StopFactorGroup> findAllCurrent();

    /** Current versions of the active groups, in display order: what a check uses. */
    @Query(value = """
            SELECT g.*
            FROM stop_factors.stop_factor_group g
            WHERE g.valid_to IS NULL AND g.is_active
            ORDER BY g.order_number, g.id
            """, nativeQuery = true)
    List<StopFactorGroup> findAllCurrentActive();

    @Query(value = """
            SELECT g.*
            FROM stop_factors.stop_factor_group g
            WHERE g.valid_to IS NULL AND COALESCE(g.root_id, g.id) = :groupId
            """, nativeQuery = true)
    Optional<StopFactorGroup> findCurrent(long groupId);

    /** Every version of a group, newest first. */
    @Query(value = """
            SELECT g.*
            FROM stop_factors.stop_factor_group g
            WHERE COALESCE(g.root_id, g.id) = :groupId
            ORDER BY g.version_number DESC
            """, nativeQuery = true)
    List<StopFactorGroup> findVersions(long groupId);

    // Every change of a group first locks its first version, so concurrent edits run one after another.
    // The first version is locked, not the current one: the current one stops matching valid_to IS NULL once an edit commits
    @Query(value = """
            SELECT g.*
            FROM stop_factors.stop_factor_group g
            WHERE g.id = :groupId AND g.root_id IS NULL
            FOR UPDATE
            """, nativeQuery = true)
    Optional<StopFactorGroup> lockGroup(long groupId);

    @Query(value = "SELECT COALESCE(MAX(g.order_number), 0) FROM stop_factors.stop_factor_group g WHERE g.valid_to IS NULL",
            nativeQuery = true)
    int findMaxCurrentOrderNumber();

    /**
     * Product of the application being scored. Empty if the scoring has no product (legacy scorings) -
     * then product limits are ignored and the default segment is used.
     */
    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.description AS description, p.segment_type AS segmentType
            FROM public.scoring s
            JOIN public.product p ON p.id = s.product_id
            WHERE s.id = :scoringId
            """, nativeQuery = true)
    Optional<ProductProjection> findProductByScoringId(long scoringId);

    /** Products with these ids; ids without a product are not returned. */
    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.description AS description, p.segment_type AS segmentType
            FROM public.product p
            WHERE p.id IN (:productIds)
            """, nativeQuery = true)
    List<ProductProjection> findProductsByIds(Collection<Long> productIds);
}
