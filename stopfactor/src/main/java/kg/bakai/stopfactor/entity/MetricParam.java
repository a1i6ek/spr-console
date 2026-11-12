package kg.bakai.stopfactor.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A param of a metric, stored in its params JSON: the key the calculator reads, the label in the console and whether
 * a stop factor must set it.
 */
public record MetricParam(
        @NotBlank @Size(max = 64) String key,
        @NotBlank @Size(max = 255) String label,
        boolean required
) {
}
