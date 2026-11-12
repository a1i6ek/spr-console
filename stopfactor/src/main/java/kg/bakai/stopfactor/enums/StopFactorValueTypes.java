package kg.bakai.stopfactor.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Type of the threshold values of a stop factor; the label is what the console and messages call it. Keep in sync
 * with ck_stop_factor_value_type.
 */
@Getter
@RequiredArgsConstructor
public enum StopFactorValueTypes {
    /** One loan status. */
    STRING("Статус"),
    /** A whole number: the highest allowed value. */
    INTEGER("Целое число"),
    /** A number: the highest allowed value. */
    DOUBLE("Число"),
    /** A list of loan statuses separated by commas. */
    ARRAY("Список статусов");

    private final String label;
}
