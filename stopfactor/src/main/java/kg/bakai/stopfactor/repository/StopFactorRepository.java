package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactor;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stop factors. Their group, metric, conditions and products load lazily in batches
 * (hibernate.default_batch_fetch_size), so callers run in a transaction.
 */
public interface StopFactorRepository extends JpaRepository<StopFactor, Long> {
}
