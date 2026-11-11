package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StopFactorRepository extends JpaRepository<StopFactor, Long> {

    /** Whether a stop factor of any version of any group of this metric has a value of this param. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM stop_factors.stop_factor f
                           WHERE f.metric_id = :metricId AND jsonb_exists(f.params, :key))
            """, nativeQuery = true)
    boolean isParamUsed(long metricId, String key);
}
