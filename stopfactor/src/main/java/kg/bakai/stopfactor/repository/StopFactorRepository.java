package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.repository.projection.ProductProjection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stop factors in their boxes. The current box holds the stop factors that are checked; a stop factor (factorId) is
 * in every box made while it existed. Conditions and products load lazily in batches
 * (hibernate.default_batch_fetch_size), so callers run in a transaction.
 */
public interface StopFactorRepository extends JpaRepository<StopFactor, Long> {

    /** What a check runs: the active stop factors of the current box, in check order. */
    @Query("""
            SELECT f
            FROM StopFactor f
            JOIN FETCH f.metric
            WHERE f.group.validTo IS NULL AND f.active = true
            ORDER BY f.orderNumber, f.factorId
            """)
    List<StopFactor> findCurrentActive();

    /** Every stop factor of the current box, in check order. */
    @Query("""
            SELECT f
            FROM StopFactor f
            JOIN FETCH f.group g
            JOIN FETCH f.metric
            WHERE g.validTo IS NULL
            ORDER BY f.orderNumber, f.factorId
            """)
    List<StopFactor> findAllCurrent();

    @Query("SELECT f FROM StopFactor f JOIN FETCH f.group g WHERE f.factorId = :factorId AND g.validTo IS NULL")
    Optional<StopFactor> findCurrent(long factorId);

    /** A stop factor in every box it was in, oldest box first: its history. */
    @Query("""
            SELECT f FROM StopFactor f JOIN FETCH f.group g JOIN FETCH f.metric
            WHERE f.factorId = :factorId
            ORDER BY g.versionNumber
            """)
    List<StopFactor> findHistory(long factorId);

    /**
     * For every stop factor of the current box, when its current version started and why: the first box that version
     * was in. Rows of (factor id, valid from, change reason).
     */
    @Query(value = """
            SELECT cur.factor_id, s.valid_from, s.change_reason
            FROM stop_factors.stop_factor cur
            JOIN stop_factors.stop_factor_group cg ON cg.id = cur.group_id AND cg.valid_to IS NULL
            CROSS JOIN LATERAL (
                SELECT g.valid_from, g.change_reason
                FROM stop_factors.stop_factor f
                JOIN stop_factors.stop_factor_group g ON g.id = f.group_id
                WHERE f.factor_id = cur.factor_id AND f.version_number = cur.version_number
                ORDER BY g.version_number
                LIMIT 1) s
            """, nativeQuery = true)
    List<Object[]> findCurrentVersionStarts();

    /** The id of a new stop factor; from the rows' sequence, so it is no other stop factor's id. */
    @Query(value = "SELECT nextval('public.stop_factor_id_generator')", nativeQuery = true)
    long nextFactorId();

    /** How many checks each version of a stop factor made, by result: rows of (version number, result, count). */
    @Query(value = """
            SELECT f.version_number, c.result, count(*)
            FROM stop_factors.stop_factor_check c
            JOIN stop_factors.stop_factor f ON f.id = c.stop_factor_id
            WHERE f.factor_id = :factorId
            GROUP BY f.version_number, c.result
            """, nativeQuery = true)
    List<Object[]> countChecks(long factorId);

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
