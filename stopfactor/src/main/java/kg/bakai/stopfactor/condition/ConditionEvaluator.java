package kg.bakai.stopfactor.condition;

import kg.bakai.spr.core.kib.dto.enums.LoanStatus;
import kg.bakai.stopfactor.calculator.CheckedValue;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static kg.bakai.stopfactor.enums.StopFactorStatus.APPROVED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorStatus.UNDERWRITER;

/**
 * Applies the thresholds of a stop factor to its measured value: the REJECTED threshold first, the UNDERWRITER
 * threshold only if it did not reject.
 * <ul>
 *     <li>a number condition fires when its {@link ConditionOperator} holds for the value
 *     ({@code >, >=, <, <=, =}, RANGE {@code value < x <= value_to}, BETWEEN {@code value <= x <= value_to});</li>
 *     <li>{@link ConditionOperator#IN}: fires when a loan of the client has one of the statuses; the checked value
 *     is that status, or {@link #NO_MATCHES}.</li>
 * </ul>
 */
@Slf4j
public final class ConditionEvaluator {

    /** Checked value of a status condition when no loan of the client has one of its statuses. */
    public static final String NO_MATCHES = "Нет совпадений";

    private static final List<StopFactorStatus> ORDER = List.of(REJECTED, UNDERWRITER);
    private static final Set<String> LOAN_STATUSES = Arrays.stream(LoanStatus.values()).map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private ConditionEvaluator() {
    }

    public record Outcome(StopFactorStatus result, String checkedValue) {
    }

    /** A threshold of a stop factor: the result it gives when the operator holds for the measured value. */
    public record Threshold(StopFactorStatus result, ConditionOperator operator, String value, String valueTo) {
    }

    public static Outcome apply(CheckedValue value, List<Threshold> thresholds) {
        Outcome outcome = new Outcome(APPROVED, String.valueOf(number(value)));
        for (StopFactorStatus result : ORDER) {
            Optional<Threshold> threshold = thresholds.stream().filter(t -> t.result() == result).findFirst();
            if (threshold.isPresent()) {
                outcome = test(threshold.get(), value);
                if (outcome.result() == REJECTED) {
                    break;
                }
            }
        }
        return outcome;
    }

    private static Outcome test(Threshold threshold, CheckedValue value) {
        return switch (threshold.operator()) {
            case IN -> statusOutcome(threshold, value);
            default -> {
                double number = number(value);
                double valueTo = threshold.operator().hasValueTo() ? Double.parseDouble(threshold.valueTo().trim()) : Double.NaN;
                boolean fires = threshold.operator().test(number, Double.parseDouble(threshold.value().trim()), valueTo);
                yield new Outcome(fires ? threshold.result() : APPROVED, String.valueOf(number));
            }
        };
    }

    private static Outcome statusOutcome(Threshold threshold, CheckedValue value) {
        Set<String> statuses = statuses(threshold.value());
        return value.values().stream()
                .filter(statuses::contains)
                .findFirst()
                .map(status -> new Outcome(threshold.result(), status))
                .orElseGet(() -> new Outcome(APPROVED, NO_MATCHES));
    }

    /** The number of a value; a list value (loan statuses) counts as 0, as in legacy. */
    private static double number(CheckedValue value) {
        return value.isNumber() ? value.number() : 0;
    }

    /** A status unknown to LoanStatus is skipped with a warning; legacy LoanStatus.valueOf failed the whole check. */
    private static Set<String> statuses(String conditionValue) {
        Set<String> statuses = new HashSet<>();
        for (String item : conditionValue.split(",")) {
            String name = item.trim();
            if (LOAN_STATUSES.contains(name)) {
                statuses.add(name);
            } else if (!name.isEmpty()) {
                log.warn("Неизвестный статус кредита '{}' в пороге стоп-фактора, пропущен", name);
            }
        }
        return statuses;
    }
}
