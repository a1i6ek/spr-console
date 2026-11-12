package kg.bakai.stopfactor.dto;

import kg.bakai.stopfactor.entity.MetricParamSchema;
import kg.bakai.stopfactor.enums.CalculatorType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A metric version. In a request only title, calculator, nameTemplate, params, active and changeReason are read.
 *
 * @param id            the metric: the id of its first version, the same for all its versions
 * @param versionId     this version; stop factors refer to it
 * @param params        left out when created: the params of the calculator
 * @param changeReason  required when the title, name template or params change: they create a new version
 * @param validFrom     the version is valid from this moment...
 * @param validTo       ...until this one, null - the current version
 */
public record MetricDto(
        Long id,
        Long versionId,
        Integer versionNumber,
        @NotBlank @Size(max = 255) String title,
        @NotNull CalculatorType calculator,
        String calculatorName,
        @NotBlank String nameTemplate,
        List<@NotNull @Valid MetricParamSchema> params,
        Boolean active,
        @Size(max = 255) String changeReason,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {

    /** A metric of a request. */
    public static MetricDto of(String title, CalculatorType calculator, String nameTemplate, List<MetricParamSchema> params,
                               Boolean active, String changeReason) {
        return new MetricDto(null, null, null, title, calculator, null, nameTemplate, params, active, changeReason,
                null, null, null, null);
    }
}
