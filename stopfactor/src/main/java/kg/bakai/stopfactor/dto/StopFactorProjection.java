package kg.bakai.stopfactor.dto;

import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

/** A saved check result with the box and the version of the rule it was checked with (versions never change). */
public interface StopFactorProjection {
    long getId();

    /** The box the stop factor row was in. */
    int getBoxNumber();

    /** The version of the stop factor the check ran with. */
    long getStopFactorId();

    /** The stop factor: the same for all its versions. */
    long getFactorId();

    int getFactorVersionNumber();

    String getStopFactorName();

    long getMetricId();

    String getMetricTitle();

    /** Param values as JSON, e.g. {"periodFromMonth": 7, "periodToMonth": 12}. */
    String getParams();

    Long getProductId();

    StopFactorStatus getCheckResult();

    String getCheckedValue();

    StopFactorValueTypes getRejectedValueType();

    ConditionOperator getRejectedOperator();

    String getRejectedValue();

    String getRejectedValueTo();

    StopFactorValueTypes getUnderwriterValueType();

    ConditionOperator getUnderwriterOperator();

    String getUnderwriterValue();

    String getUnderwriterValueTo();
}
