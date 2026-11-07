package kg.bakai.stopfactor.calculator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A calculator whose params are a record: every component with {@link Param} is a param, so the keys, labels and
 * checks are written once. {@link #paramSpecs()} (the console form and its checks) and {@link #parseParams} (the
 * typed params of a rule) both come from the record.
 * <pre>{@code
 * public record Window(
 *         @Param(label = "Период с (месяц)", min = 1, notAbove = "periodToMonth") int periodFromMonth,
 *         @Param(label = "Период по (месяц)", min = 1) int periodToMonth) {
 * }
 * }</pre>
 * A record that cannot be read this way fails at start, when the calculator bean is created.
 */
public abstract class RecordParamsCalculator<P extends Record> extends AbstractStopFactorCalculator<P> {

    private final Constructor<P> constructor;
    private final List<Component> components;
    private final List<ParamSpec> specs;

    /** A record component that is a param: its key, its type and whether it may be left out. */
    private record Component(String key, Class<?> type, boolean optional, Param param) {
    }

    protected RecordParamsCalculator(Class<P> paramsType) {
        if (!paramsType.isRecord()) {
            throw new IllegalStateException(paramsType.getName() + ": параметры калькулятора должны быть record");
        }
        RecordComponent[] recordComponents = paramsType.getRecordComponents();
        List<Component> read = new ArrayList<>();
        for (RecordComponent component : recordComponents) {
            Param param = component.getAnnotation(Param.class);
            if (param == null) {
                throw new IllegalStateException(paramsType.getName() + "." + component.getName() + ": нет аннотации @Param");
            }
            read.add(new Component(component.getName(), component.getType(), component.getType() == Integer.class, param));
        }
        this.components = List.copyOf(read);
        this.specs = components.stream().map(c -> toSpec(paramsType, c)).toList();
        components.stream().filter(c -> !c.param().notAbove().isEmpty()).forEach(c -> requireIntegerParam(paramsType, c));
        try {
            this.constructor = paramsType.getDeclaredConstructor(
                    Arrays.stream(recordComponents).map(RecordComponent::getType).toArray(Class<?>[]::new));
            this.constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(paramsType.getName() + ": нет канонического конструктора", e);
        }
    }

    @Override
    public final List<ParamSpec> paramSpecs() {
        return specs;
    }

    /** Periods and ranges: a param with {@link Param#notAbove()} may not be greater than that param. */
    @Override
    protected final void validateNormalized(Map<String, Object> params, List<String> errors) {
        components.stream()
                .filter(c -> !c.param().notAbove().isEmpty())
                .forEach(c -> requireOrdered(params, c.key(), c.param().notAbove(), errors));
    }

    @Override
    public final P parseParams(Map<String, Object> params) {
        Object[] values = components.stream().map(c -> value(c, params)).toArray();
        try {
            return constructor.newInstance(values);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Параметры стоп-фактора не подходят для " + constructor.getDeclaringClass().getSimpleName()
                    + ": " + params, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось создать " + constructor.getDeclaringClass().getSimpleName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object value(Component component, Map<String, Object> params) {
        if (component.type().isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) component.type(), stringParam(params, component.key()));
        }
        // Not a ?: expression: with an int branch it would unbox the Integer and fail on null
        if (component.optional()) {
            return optionalIntParam(params, component.key());
        }
        return intParam(params, component.key());
    }

    private static ParamSpec toSpec(Class<?> paramsType, Component component) {
        Class<?> type = component.type();
        String label = component.param().label();
        if (type == int.class || type == Integer.class) {
            return ParamSpec.integer(component.key(), label, !component.optional(), component.param().min());
        }
        if (type.isEnum()) {
            Set<String> names = new LinkedHashSet<>();
            Arrays.stream(type.getEnumConstants()).forEach(constant -> names.add(((Enum<?>) constant).name()));
            return ParamSpec.oneOf(component.key(), label, names);
        }
        throw new IllegalStateException(paramsType.getName() + "." + component.key() + ": тип " + type.getSimpleName()
                + " не поддерживается, допустимы int, Integer и enum");
    }

    private void requireIntegerParam(Class<?> paramsType, Component component) {
        String other = component.param().notAbove();
        boolean bothIntegers = components.stream()
                .filter(c -> c.key().equals(other) || c == component)
                .filter(c -> c.type() == int.class || c.type() == Integer.class)
                .count() == 2;
        if (!bothIntegers) {
            throw new IllegalStateException(paramsType.getName() + "." + component.key() + ": notAbove = \"" + other
                    + "\" должен ссылаться на другой целочисленный параметр");
        }
    }
}
