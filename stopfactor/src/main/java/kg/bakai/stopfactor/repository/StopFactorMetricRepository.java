package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorMetric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StopFactorMetricRepository extends JpaRepository<StopFactorMetric, Long> {

    @Query(value = "SELECT m.* FROM stop_factors.stop_factor_metric m ORDER BY m.id", nativeQuery = true)
    List<StopFactorMetric> findAllOrdered();

    /** Whether a stop factor of any group version uses the metric. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM stop_factors.stop_factor f WHERE f.metric_id = :metricId)", nativeQuery = true)
    boolean isUsed(long metricId);

    /** Stop factors of the current group versions that use the metric. */
    @Query(value = """
            SELECT f.*
            FROM stop_factors.stop_factor f
            JOIN stop_factors.stop_factor_group g ON g.id = f.group_id
            WHERE g.valid_to IS NULL AND f.metric_id = :metricId
            """, nativeQuery = true)
    List<StopFactor> findCurrentStopFactors(long metricId);
}
