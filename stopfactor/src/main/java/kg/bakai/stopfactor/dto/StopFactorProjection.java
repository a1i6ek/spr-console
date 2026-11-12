package kg.bakai.stopfactor.dto;

import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

/** A saved check result with the group version and the rule it was checked with (those rows never change). */
public interface StopFactorProjection {
    long getId();

    /** The group: the id of its first version. */
    long getGroupId();

    long getGroupVersionId();

    int getGroupVersionNumber();

    String getGroupName();

    long getStopFactorId();

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
