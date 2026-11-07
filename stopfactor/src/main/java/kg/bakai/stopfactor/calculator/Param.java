package kg.bakai.stopfactor.calculator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A param of a stop factor rule, declared on a component of the params record of a {@link RecordParamsCalculator}.
 * The component name is the key the rule stores ({@code periodFromMonth}); its type sets the kind of param:
 * {@code int} - a required whole number, {@code Integer} - a whole number that may be left out, an enum - one of
 * its constants.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Param {

    /** Name of the param in the console and in messages. */
    String label();

    /** Smallest value of a whole number. */
    int min() default 0;

    /**
     * Key of a whole number param this one may not be greater than: the start of a period or range, e.g.
     * {@code periodFromMonth} with {@code notAbove = "periodToMonth"}.
     */
    String notAbove() default "";
}
