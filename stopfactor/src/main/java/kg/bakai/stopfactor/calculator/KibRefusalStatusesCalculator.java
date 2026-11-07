package kg.bakai.stopfactor.calculator;

import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.calculator.NoParams;
import kg.bakai.stopfactor.calculator.ParameterlessCalculator;
import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.report.fact.CheckContext;
import kg.bakai.stopfactor.report.fact.Facts;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class KibRefusalStatusesCalculator extends ParameterlessCalculator {

    @Override
    public CalculatorType type() {
        return CalculatorType.KIB_REFUSAL_STATUSES;
    }

    @Override
    public ValueKind valueKind() {
        return ValueKind.LIST;
    }

    /** Statuses of the KIB loans of the client; the condition of the stop factor lists the refusal ones. */
    @Override
    public Optional<CheckedValue> calculate(NoParams params, CheckContext context) {
        return Optional.of(CheckedValue.ofValues(context.find(Facts.LOAN_STATUSES).orElse(List.of())));
    }
}
