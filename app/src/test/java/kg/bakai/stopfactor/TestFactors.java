package kg.bakai.stopfactor;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Console requests of the integration tests. */
final class TestFactors {

    /** Metrics of changelog 1.3. */
    static final long ACTIVE_DISPUTES_METRIC = 2;
    static final long CURRENT_OVERDUE_DAYS_METRIC = 6;
    static final long OVERDUE_METRIC = 10;

    private TestFactors() {
    }

    /** A new stop factor. */
    static StopFactorRequest create(StopFactorDto f) {
        return request(f, null);
    }

    /** A change of a stop factor: the values of {@code f}, with the reason. */
    static StopFactorRequest change(StopFactorResponse current, StopFactorDto f, String reason) {
        return request(f, reason);
    }

    static StopFactorRequest request(StopFactorDto f, String reason) {
        return new StopFactorRequest(f.metricId(), f.name(), f.description(), f.active(), f.segment(),
                f.params(), f.conditions(), f.productIds(), reason);
    }

    /** The rule of a current stop factor, to change it. */
    static StopFactorDto dto(StopFactorResponse r) {
        return new StopFactorDto(r.versionId(), r.metric().id(), r.metric().title(), r.name(), r.description(), r.active(),
                r.segment(), r.params(), r.conditions(), r.productIds(), r.summary());
    }

    /** "Active disputes" with these thresholds, for every product. */
    static StopFactorDto disputes(ConditionOperator rejected, String rejectedValue, String rejectedValueTo,
                                  ConditionOperator underwriter, String underwriterValue, String underwriterValueTo) {
        List<ConditionDto> conditions = new ArrayList<>();
        if (rejected != null) {
            conditions.add(new ConditionDto(StopFactorStatus.REJECTED, rejected, rejectedValue, rejectedValueTo, StopFactorValueTypes.INTEGER));
        }
        if (underwriter != null) {
            conditions.add(new ConditionDto(StopFactorStatus.UNDERWRITER, underwriter, underwriterValue, underwriterValueTo,
                    StopFactorValueTypes.INTEGER));
        }
        return new StopFactorDto(null, ACTIVE_DISPUTES_METRIC, null, "Активные споры", null, true, null, Map.of(), conditions,
                Set.of(), null);
    }

    /** "Active disputes" rejecting above 0, limited to a segment and products. */
    static StopFactorDto disputes(ProductSegment segment, Set<Long> productIds) {
        return new StopFactorDto(null, ACTIVE_DISPUTES_METRIC, null, "Активные споры", null, true, segment, Map.of(),
                List.of(rejectedAbove("0")), productIds, null);
    }

    /** Overdue history of these params rejecting above 0. */
    static StopFactorDto overdue(Map<String, Object> params) {
        return new StopFactorDto(null, OVERDUE_METRIC, null, "Просрочки", null, true, null, params,
                List.of(rejectedAbove("0")), Set.of(), null);
    }

    /** The same stop factor with another REJECTED value. */
    static StopFactorDto withRejectedValue(StopFactorDto f, String rejectedValue) {
        List<ConditionDto> conditions = f.conditions().stream()
                .map(c -> c.result() == StopFactorStatus.REJECTED
                        ? new ConditionDto(c.result(), c.operator(), rejectedValue, c.valueTo(), c.valueType())
                        : c)
                .toList();
        return new StopFactorDto(null, f.metricId(), null, f.name(), f.description(), f.active(), f.segment(), f.params(),
                conditions, f.productIds(), f.summary());
    }

    /** REJECTED when x > value. */
    static ConditionDto rejectedAbove(String value) {
        return new ConditionDto(StopFactorStatus.REJECTED, ConditionOperator.GT, value, null, StopFactorValueTypes.INTEGER);
    }

    /** The value of a condition of a result, null when there is none. */
    static String valueOf(StopFactorDto f, StopFactorStatus result) {
        return f.conditions().stream().filter(c -> c.result() == result).map(ConditionDto::value).findFirst().orElse(null);
    }
}
