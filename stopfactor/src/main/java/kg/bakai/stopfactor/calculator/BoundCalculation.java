package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.report.fact.CheckContext;

import java.util.Optional;

/**
 * A calculator with the parsed params of one stop factor: params are parsed once, not on every check.
 */
public record BoundCalculation<P>(StopFactorCalculator<P> calculator, P params) {

    public Optional<CheckedValue> calculate(CheckContext context) {
        return calculator.calculate(params, context);
    }
}
