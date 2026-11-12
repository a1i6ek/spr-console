package kg.bakai.stopfactor.validation;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.condition.ConditionValues;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.MetricParam;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorFormatter;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.enums.ValueKind;
import kg.bakai.stopfactor.exception.InvalidParamsException;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks a stop factor from the console against its metric and returns it normalized, or the problems found.
 * What is checked, in the order of {@link #validate(StopFactorDto, StopFactorMetric, List)}: the name, the param
 * values against the metric params (then normalized by the calculator), the conditions against the calculator's
 * value kind, the products and the segment. The caller prefixes the errors it collects.
 */
@Component
@RequiredArgsConstructor
public class StopFactorValidation {

    private static final Set<StopFactorStatus> RESULTS = EnumSet.of(StopFactorStatus.REJECTED, StopFactorStatus.UNDERWRITER);

    private final CalculatorRegistry calculatorRegistry;
    private final StopFactorGroupRepository groupRepository;

    // ================================================================================================
    // A stop factor
    // ================================================================================================

    /**
     * The stop factor normalized, or null with the problems added to errors.
     *
     * @param metric the current metric of the stop factor (the caller found it by metricId)
     */
    public StopFactorDto validate(StopFactorDto request, StopFactorMetric metric, List<String> errors) {
        int errorsBefore = errors.size();
        if (isBlank(request.name())) {
            errors.add("Укажите название стоп-фактора.");
        }
        ValueKind kind = calculatorRegistry.get(metric.getCalculator()).valueKind();
        Map<String, Object> params = validateParams(metric, request.params(), errors);

        List<ConditionDto> conditions = validateConditions(request.conditions(), kind, errors);

        Set<Long> productIds = request.productIds() != null ? request.productIds() : Set.of();
        ProductSegment segment = validateProducts(productIds, request.segment(), errors);

        if (errors.size() > errorsBefore) {
            return null;
        }
        return new StopFactorDto(
                null,
                metric.getId(),
                metric.getTitle(),
                request.name().trim(),
                isBlank(request.description()) ? null : request.description().trim(),
                request.active() == null || request.active(),
                segment,
                params,
                conditions,
                productIds,
                StopFactorFormatter.summary(metric.getTitle(), metric.getParams(), params));
    }

    /**
     * Param values of a stop factor: every key a param of the metric, every required param set; then the calculator
     * checks and normalizes the values.
     */
    public Map<String, Object> validateParams(StopFactorMetric metric, Map<String, Object> params, List<String> errors) {
        Map<String, Object> source = params != null ? params : Map.of();
        int errorsBefore = errors.size();
        source.keySet().stream()
                .filter(key -> metric.findParam(key).isEmpty())
                .forEach(key -> errors.add("Параметр «" + key + "» не задан в метрике «" + metric.getTitle() + "»."));
        metric.getParams().stream()
                .filter(MetricParam::required)
                .filter(p -> source.get(p.key()) == null || (source.get(p.key()) instanceof String s && s.isBlank()))
                .forEach(p -> errors.add("Укажите «" + p.label() + "»."));
        if (errors.size() > errorsBefore) {
            return Map.of();
        }
        try {
            return calculatorRegistry.get(metric.getCalculator()).normalizeParams(source);
        } catch (InvalidParamsException e) {
            errors.addAll(e.getErrors());
            return Map.of();
        }
    }

    // ================================================================================================
    // Conditions: at most one of each result, fitting the calculator, with a threshold it can fire on
    // ================================================================================================

    /**
     * The conditions normalized, REJECTED first: REJECTED or UNDERWRITER, at most one of each, a value type and an
     * operator that fit the calculator, valueTo exactly for RANGE and BETWEEN, and an UNDERWRITER condition that can
     * fire where REJECTED does not.
     */
    private List<ConditionDto> validateConditions(List<ConditionDto> sent, ValueKind kind, List<String> errors) {
        List<ConditionDto> conditions = new ArrayList<>();
        Set<StopFactorStatus> seen = EnumSet.noneOf(StopFactorStatus.class);
        for (ConditionDto condition : sent != null ? sent : List.<ConditionDto>of()) {
            if (condition.result() == null || !RESULTS.contains(condition.result())) {
                errors.add("Результат условия должен быть «" + StopFactorStatus.REJECTED.getLabel() + "» или «"
                        + StopFactorStatus.UNDERWRITER.getLabel() + "».");
            } else if (!seen.add(condition.result())) {
                errors.add("Условие «" + condition.result().getLabel() + "» указано несколько раз: у стоп-фактора может быть"
                        + " только одно такое условие.");
            } else {
                ConditionDto valid = normalizedCondition(condition, kind, errors);
                if (valid != null) {
                    conditions.add(valid);
                }
            }
        }
        conditions.sort(Comparator.comparing(ConditionDto::result));
        Optional<ConditionDto> rejected = conditions.stream().filter(c -> c.result() == StopFactorStatus.REJECTED).findFirst();
        Optional<ConditionDto> underwriter = conditions.stream().filter(c -> c.result() == StopFactorStatus.UNDERWRITER).findFirst();
        if (rejected.isPresent() && underwriter.isPresent()) {
            checkUnderwriterCanFire(kind, rejected.get(), underwriter.get(), errors);
        }
        return conditions;
    }

    /** The condition normalized; null when it has an error (added to errors). */
    private ConditionDto normalizedCondition(ConditionDto sent, ValueKind kind, List<String> errors) {
        String prefix = "Условие «" + sent.result().getLabel() + "»: ";
        if (sent.operator() == null) {
            errors.add(prefix + "укажите оператор.");
            return null;
        }
        if (isBlank(sent.value())) {
            errors.add(prefix + "укажите значение.");
            return null;
        }
        if (sent.valueType() == null) {
            errors.add(prefix + "укажите тип значения.");
            return null;
        }
        if (!kind.conditionTypes().contains(sent.valueType())) {
            errors.add(prefix + "тип значения «" + sent.valueType().getLabel() + "» не подходит для калькулятора. Допустимые типы: "
                    + kind.conditionTypes().stream().sorted().map(t -> "«" + t.getLabel() + "»").collect(Collectors.joining(", ")) + ".");
            return null;
        }
        if (!kind.operators().contains(sent.operator())) {
            errors.add(prefix + "оператор «" + sent.operator().getName() + "» не подходит для калькулятора. Допустимые операторы: "
                    + kind.operators().stream().map(o -> "«" + o.getName() + "»").collect(Collectors.joining(", ")) + ".");
            return null;
        }
        String value = normalizeValue(sent.valueType(), sent.value(), prefix, errors);
        if (value == null) {
            return null;
        }
        return withBounds(sent, value, prefix, errors);
    }

    /** RANGE and BETWEEN with their upper bound, checked to be above the lower one; the other operators with none. */
    private ConditionDto withBounds(ConditionDto sent, String value, String prefix, List<String> errors) {
        if (!sent.operator().hasValueTo()) {
            if (!isBlank(sent.valueTo())) {
                errors.add(prefix + "верхняя граница задаётся только для операторов «" + ConditionOperator.RANGE.getName()
                        + "» и «" + ConditionOperator.BETWEEN.getName() + "».");
                return null;
            }
            return new ConditionDto(sent.result(), sent.operator(), value, null, sent.valueType());
        }
        if (isBlank(sent.valueTo())) {
            errors.add(prefix + "укажите верхнюю границу для оператора «" + sent.operator().getName() + "».");
            return null;
        }
        String valueTo = normalizeValue(sent.valueType(), sent.valueTo(), prefix + "верхняя граница: ", errors);
        if (valueTo == null) {
            return null;
        }
        if (ConditionValues.parseNumber(valueTo).compareTo(ConditionValues.parseNumber(value)) <= 0) {
            errors.add(prefix + "верхняя граница (" + valueTo + ") должна быть больше нижней (" + value + ").");
            return null;
        }
        return new ConditionDto(sent.result(), sent.operator(), value, valueTo, sent.valueType());
    }

    // ================================================================================================
    // Values
    // ================================================================================================

    private static String normalizeValue(StopFactorValueTypes type, String raw, String prefix, List<String> errors) {
        return switch (type) {
            case INTEGER, DOUBLE -> normalizeNumber(type, raw, prefix, errors);
            case ARRAY, STRING -> normalizeStatuses(type, raw, prefix, errors);
        };
    }

    private static String normalizeNumber(StopFactorValueTypes type, String raw, String prefix, List<String> errors) {
        String value = raw.trim();
        try {
            BigDecimal number = ConditionValues.parseNumber(value);
            if (number.signum() < 0) {
                errors.add(prefix + "значение не может быть отрицательным.");
                return null;
            }
            return type == StopFactorValueTypes.INTEGER
                    ? number.toBigIntegerExact().toString()
                    : number.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException | ArithmeticException e) {
            errors.add(prefix + "значение должно быть " + (type == StopFactorValueTypes.INTEGER ? "целым числом" : "числом")
                    + ", получено «" + value + "».");
            return null;
        }
    }

    private static String normalizeStatuses(StopFactorValueTypes type, String raw, String prefix, List<String> errors) {
        String value = raw.trim();
        List<String> items = Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (items.isEmpty()) {
            errors.add(prefix + "укажите хотя бы один статус кредита.");
            return null;
        }
        if (type == StopFactorValueTypes.STRING && items.size() > 1) {
            errors.add(prefix + "тип «" + StopFactorValueTypes.STRING.getLabel() + "» принимает один статус."
                    + " Для нескольких статусов выберите тип «" + StopFactorValueTypes.ARRAY.getLabel() + "».");
            return null;
        }
        Set<String> unique = new HashSet<>();
        for (String item : items) {
            if (!unique.add(ConditionValues.normalizeItem(item))) {
                errors.add(prefix + "статус «" + item + "» указан несколько раз.");
                return null;
            }
        }
        return String.join(", ", items);
    }

    // ================================================================================================
    // Products and segment
    // ================================================================================================

    /**
     * The products a stop factor is limited to must exist and be of its segment; without a segment it takes the
     * segment of the products when they all share one, so a scoring without product gets the same stop factors.
     */
    private ProductSegment validateProducts(Set<Long> productIds, ProductSegment segment, List<String> errors) {
        if (productIds.isEmpty()) {
            return segment;
        }
        List<ProductProjection> products = groupRepository.findProductsByIds(productIds).stream()
                .sorted(Comparator.comparing(ProductProjection::getId))
                .toList();
        Set<Long> found = products.stream().map(ProductProjection::getId).collect(Collectors.toSet());
        productIds.stream().filter(id -> !found.contains(id)).sorted()
                .forEach(id -> errors.add("Продукт с id " + id + " не найден."));
        if (segment == null) {
            Set<ProductSegment> segments = products.stream().map(ProductProjection::getSegment).collect(Collectors.toSet());
            return segments.size() == 1 ? segments.iterator().next() : null;
        }
        products.stream()
                .filter(product -> product.getSegment() != segment)
                .forEach(product -> errors.add("Продукт «" + product.getName() + "» (id " + product.getId() + ") относится к сегменту "
                        + product.getSegment() + ", а стоп-фактор к сегменту " + segment + "."));
        return segment;
    }

    // ================================================================================================
    // REJECTED is checked first, so UNDERWRITER must fire on some value REJECTED does not
    // ================================================================================================

    private static void checkUnderwriterCanFire(ValueKind kind, ConditionDto rejected, ConditionDto underwriter, List<String> errors) {
        if (kind == ValueKind.NUMBER) {
            if (NumberSet.of(underwriter).within(NumberSet.of(rejected))) {
                errors.add("Условие «" + StopFactorStatus.UNDERWRITER.getLabel() + "» ("
                        + underwriter.operator().formula(underwriter.value(), underwriter.valueTo())
                        + ") никогда не сработает: при всех этих значениях уже срабатывает «" + StopFactorStatus.REJECTED.getLabel()
                        + "» (" + rejected.operator().formula(rejected.value(), rejected.valueTo()) + "), который проверяется первым.");
            }
        } else {
            Set<String> overlap = new HashSet<>(ConditionValues.parseItems(rejected.value()));
            overlap.retainAll(ConditionValues.parseItems(underwriter.value()));
            if (!overlap.isEmpty()) {
                errors.add("Статусы " + String.join(", ", new TreeSet<>(overlap)) + " указаны в обоих условиях. Для них всегда"
                        + " срабатывает «" + StopFactorStatus.REJECTED.getLabel() + "», уберите их из условия «"
                        + StopFactorStatus.UNDERWRITER.getLabel() + "».");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The numbers a number threshold fires on, an interval: a null bound is unbounded. */
    private record NumberSet(BigDecimal from, boolean fromIncluded, BigDecimal to, boolean toIncluded) {

        static NumberSet of(ConditionDto t) {
            BigDecimal value = ConditionValues.parseNumber(t.value());
            BigDecimal valueTo = t.valueTo() != null ? ConditionValues.parseNumber(t.valueTo()) : null;
            return switch (t.operator()) {
                case GT -> new NumberSet(value, false, null, false);
                case GTE -> new NumberSet(value, true, null, false);
                case LT -> new NumberSet(null, false, value, false);
                case LTE -> new NumberSet(null, false, value, true);
                case EQ -> new NumberSet(value, true, value, true);
                case RANGE -> new NumberSet(value, false, valueTo, true);
                case BETWEEN -> new NumberSet(value, true, valueTo, true);
                case IN -> throw new IllegalArgumentException("IN is not a number threshold");
            };
        }

        boolean within(NumberSet other) {
            return startsWithin(other) && endsWithin(other);
        }

        private boolean startsWithin(NumberSet other) {
            if (other.from == null) {
                return true;
            }
            if (from == null) {
                return false;
            }
            int compare = from.compareTo(other.from);
            return compare > 0 || compare == 0 && (other.fromIncluded || !fromIncluded);
        }

        private boolean endsWithin(NumberSet other) {
            if (other.to == null) {
                return true;
            }
            if (to == null) {
                return false;
            }
            int compare = to.compareTo(other.to);
            return compare < 0 || compare == 0 && (other.toIncluded || !toIncluded);
        }
    }
}
