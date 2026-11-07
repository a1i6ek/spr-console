package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.calculator.NoParams;
import kg.bakai.stopfactor.calculator.ParameterlessCalculator;
import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.ReportValues;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

/** A value of the reports as is, without params: disputes, current overdue, open credits. */
public class SimpleValueCalculator extends ParameterlessCalculator {

    private final CalculatorType type;
    private final ToDoubleFunction<ReportValues> extractor;

    public SimpleValueCalculator(CalculatorType type, ToDoubleFunction<ReportValues> extractor) {
        this.type = type;
        this.extractor = extractor;
    }

    @Override
    public CalculatorType type() {
        return type;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.NUMBER;
    }

    @Override
    public Optional<CheckedValue> calculate(NoParams params, CheckContext context) {
        return Optional.of(CheckedValue.ofNumber(extractor.applyAsDouble(context.get(Facts.REPORT_VALUES))));
    }
}
