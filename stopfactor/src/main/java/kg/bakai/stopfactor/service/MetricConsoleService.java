package kg.bakai.stopfactor.service;

import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;

import java.time.LocalDateTime;
import java.util.List;

public interface MetricConsoleService {

    List<CalculatorDto> getCalculators();

    /** Current metric versions, or the versions valid at a moment when it is given. */
    List<MetricDto> getAll(LocalDateTime at);

    /** Current version of a metric. */
    MetricDto get(long metricId);

    /** All versions of a metric, newest first. */
    List<MetricDto> getVersions(long metricId);

    MetricDto create(MetricDto request);

    /**
     * A new title, name template or params create a new version: the current one is closed, and every current group
     * with a stop factor of the metric gets a new version that uses the new metric version. Earlier group versions
     * and checks keep the metric version they were made with. The active flag alone is changed in place.
     */
    MetricDto update(long metricId, MetricDto request);

    /** Only while no stop factor of any group version uses any version of the metric; otherwise deactivate it. */
    void delete(long metricId);
}
