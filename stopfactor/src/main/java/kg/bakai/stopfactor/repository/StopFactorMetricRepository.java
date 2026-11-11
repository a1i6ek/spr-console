package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactorMetric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StopFactorMetricRepository extends JpaRepository<StopFactorMetric, Long> {

    /**
     * Metric by its title ("Активные споры КИБ") or name template ("Количество активных споров КИБ").
     * Used where a metric is looked up by the name a client knows it by.
     */
    Optional<StopFactorMetric> findFirstByTitleOrNameTemplateOrderById(String title, String nameTemplate);
}
