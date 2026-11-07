package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.fact.CheckContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Measures one value of an application for the stop factors of a metric.
 *
 * @param <P> typed params of a stop factor, parsed once from its stored params
 */
public interface StopFactorCalculator<P> {

    CalculatorType type();

    ValueKind valueKind();

    List<ParamSpec> paramSpecs();

    /**
     * Console: checks and normalizes the params of a stop factor before they are stored.
     *
     * @throws InvalidParamsException with every problem found
     */
    Map<String, Object> normalizeParams(Map<String, Object> params);

    /**
     * Typed params from stored (normalized) params.
     *
     * @throws IllegalStateException if the stored params are not valid for this calculator
     */
    P parseParams(Map<String, Object> params);

    /**
     * @return the value, or empty if the reports have no value for these params (the stop factor gets NO_DATA)
     */
    Optional<CheckedValue> calculate(P params, CheckContext context);

    /** This calculator with the parsed params of one stop factor. */
    default BoundCalculation<P> bind(Map<String, Object> params) {
        return new BoundCalculation<>(this, parseParams(params));
    }
}
