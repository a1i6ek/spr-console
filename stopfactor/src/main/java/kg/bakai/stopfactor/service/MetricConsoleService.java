package kg.bakai.stopfactor.service;

import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;

import java.util.List;

/** Metrics in the console: what a stop factor measures and which params it sets. Edited in place. */
public interface MetricConsoleService {

    List<MetricDto> getAll();

    MetricDto get(long metricId);

    MetricDto create(MetricDto metric);

    /**
     * Changes the title, params or active flag; the calculator cannot change. Refused when the new params would make
     * a stop factor of a current group version invalid.
     */
    MetricDto update(long metricId, MetricDto metric);

    /** Only while no stop factor of any group version uses it; otherwise deactivate it. */
    void delete(long metricId);

    /** What a metric can be built on: the calculators with their params and allowed thresholds. */
    List<CalculatorDto> getCalculators();
}
