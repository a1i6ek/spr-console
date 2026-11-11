package kg.bakai.stopfactor.dto;

import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

public interface StopFactorProjection {
    /** id of the group (its first version). */
    long getStopFactorId();
    /** id of the group version the check was made with. */
    long getStopFactorVersionId();
    int getVersionNumber();
    String getStopFactorName();
    /** id of the stop factor the check was made with. */
    long getFactorId();
    /** Name of the stop factor; for a group with one stop factor it is the group name. */
    String getFactorName();
    StopFactorStatus getCheckResult();
    String getCheckedValue();
    String getRejectedValue();
    StopFactorValueTypes getRejectedValueType();
    ConditionOperator getRejectedOperator();
    String getUnderwriterValue();
    StopFactorValueTypes getUnderwriterValueType();
    ConditionOperator getUnderwriterOperator();
    /** Upper bound of the UNDERWRITER RANGE (the REJECTED value), null for GT and IN. */
    String getUnderwriterValueTo();
}
